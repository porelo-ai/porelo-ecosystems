package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Answers shaped as each vendor's were measured. */
class ToolDownloadsTest {
    private static final String SHA256 =
            "d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c";
    private static final String SHA512 = "a".repeat(128);
    private static final String DIST =
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/";

    private final Map<String, String> answers = new HashMap<>();
    private final ToolDownloads tools =
            new ToolDownloads(
                    uri -> {
                        String answer = answers.get(uri.toString());
                        if (answer == null) {
                            throw new IOException("no such answer: " + uri);
                        }
                        return answer;
                    });

    private void temurinAnswers(int major, String json) {
        answers.put(
                "https://api.adoptium.net/v3/assets/latest/"
                        + major
                        + "/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse",
                json);
    }

    private static String temurin(String semver, String link, String checksum) {
        return """
                [{"binary":{"package":{"checksum":"%s","link":"%s"}},"version":{"semver":"%s"}}]
                """
                .formatted(checksum, link, semver);
    }

    /** Harm prevented: a build gets another JDK than asked, or none, or one it cannot check. */
    @Nested
    class Temurin {
        @Test
        void theVendorsRowGivesVersionAddressAndDigest() {
            String link = "https://github.com/adoptium/temurin21-binaries/jdk.tar.gz";
            temurinAnswers(21, temurin("21.0.13+9.0.LTS", link, SHA256));

            assertThat(tools.temurin(21))
                    .as("exactly what the vendor names")
                    .isEqualTo(new ToolDownload("21.0.13+9.0.LTS", link, SHA256));
        }

        @Test
        void anEmptyListWithStatus200IsNoJdkOfThatMajor() {
            temurinAnswers(99, "[]");

            assertThatThrownBy(() -> tools.temurin(99))
                    .as("the vendor says an unknown major so")
                    .isInstanceOf(ToolUnavailable.class)
                    .hasMessageContaining("99");
        }

        @Test
        void aVendorThatDoesNotAnswerLeavesItUnavailable() {
            assertThatThrownBy(() -> tools.temurin(21))
                    .as("offline reads as unknown, never as a crash")
                    .isInstanceOf(ToolUnavailable.class)
                    .hasMessageNotContaining("no such answer");
        }

        @Test
        void anAnswerShapedOtherwiseLeavesItUnavailable() {
            temurinAnswers(21, "{\"message\":\"down\"}");

            assertThatThrownBy(() -> tools.temurin(21))
                    .as("a changed answer is not a crash")
                    .isInstanceOf(ToolUnavailable.class);
        }

        @Test
        void somethingOtherThanJsonLeavesItUnavailable() {
            temurinAnswers(21, "<html>a proxy's page</html>");

            assertThatThrownBy(() -> tools.temurin(21))
                    .as("a proxy's page is no answer")
                    .isInstanceOf(ToolUnavailable.class);
        }
    }

    /** Harm prevented: what a vendor answered runs as root in the build of the image. */
    @Nested
    class WhatGoesIntoLines {
        @ParameterizedTest
        @CsvSource({
            "1.0, http://github.com/a.tgz, a plain connection",
            "1.0, https://evil.example/a.tgz, another host",
            "1.0, https://github.com.evil.example/a.tgz, a host that only starts alike",
            "1.0, https://github.com:8443/a.tgz, another port",
            "1.0, 'https://github.com/a.tgz'';curl x;''', a quote that ends the line's string",
            "'1.0;rm -rf /', https://github.com/a.tgz, a version with a command"
        })
        void anAnswerHoldingAnythingElseIsRefused(String version, String url, String what) {
            temurinAnswers(21, temurin(version, url, SHA256));

            assertThatThrownBy(() -> tools.temurin(21))
                    .as(what)
                    .isInstanceOf(ToolUnavailable.class);
        }

        @ParameterizedTest
        @CsvSource({"abc", "'; rm -rf /'"})
        void aDigestThatIsNoHexDigestIsRefused(String digest) {
            temurinAnswers(21, temurin("21", "https://github.com/a.tgz", digest));

            assertThatThrownBy(() -> tools.temurin(21))
                    .as("no check would catch a changed archive")
                    .isInstanceOf(ToolUnavailable.class);
        }

        @Test
        void aDigestInCapitalsIsRefused() {
            temurinAnswers(21, temurin("21", "https://github.com/a.tgz", "D".repeat(64)));

            assertThatThrownBy(() -> tools.temurin(21))
                    .as("sha256sum would refuse nothing it can compare")
                    .isInstanceOf(ToolUnavailable.class);
        }

        @Test
        void aToolBesideAnotherLeavesABrokenLinkStanding() {
            String line =
                    ToolLines.installBeside(
                            "graalvm", new ToolDownload("25", "https://github.com/a.tgz", SHA256));

            assertThat(line)
                    .as("ln stops the build on a name a broken link holds")
                    .contains(
                            "[ -e /usr/local/bin/\"${f##*/}\" ] || [ -L /usr/local/bin/\"${f##*/}\" ] ||");
        }

        @Test
        void theLinesFetchCheckUnpackAndLink() {
            String line =
                    ToolLines.install(
                            "java", new ToolDownload("21", "https://github.com/a.tgz", SHA256));

            assertThat(line)
                    .as("one RUN, checked before it is unpacked, stopping on any failure")
                    .startsWith("RUN set -eu; mkdir -p /opt/java;")
                    .contains("curl -fsSL -o /tmp/java.tgz 'https://github.com/a.tgz'")
                    .contains("echo '" + SHA256 + "  /tmp/java.tgz' | sha256sum -c -;")
                    .contains("tar xzf /tmp/java.tgz -C /opt/java --strip-components=1;")
                    .endsWith("ln -sf /opt/java/bin/* /usr/local/bin/");
        }

        @Test
        void aLongerDigestIsCheckedByItsOwnCommand() {
            String line =
                    ToolLines.install(
                            "maven", new ToolDownload("3.9.16", DIST + "a.tar.gz", SHA512));

            assertThat(line).as("Maven names a sha512").contains("| sha512sum -c -;");
        }

        @Test
        void aToolBesideAnotherLinksOnlyWhatItAdds() {
            String line =
                    ToolLines.installBeside(
                            "graalvm", new ToolDownload("25", "https://github.com/a.tgz", SHA256));

            assertThat(line)
                    .as("its java must not hide the project's")
                    .contains("[ -e /usr/local/bin/")
                    .doesNotContain("ln -sf");
        }
    }

    /** Harm prevented: a pre-release, or a build of the wrong JDK, is installed as the newest. */
    @Nested
    class Graalvm {
        @Test
        void anOlderReleasesDigestStandsInItsOwnFile() {
            String archive = "graalvm-community-jdk-21.0.2_linux-x64_bin.tar.gz";
            String base =
                    "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.2/";
            answers.put(
                    "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases?per_page=100",
                    """
                    [{"prerelease":false,"draft":false,"assets":[
                      {"name":"%1$s","browser_download_url":"%2$s%1$s","digest":null},
                      {"name":"%1$s.sha256","browser_download_url":"%2$s%1$s.sha256","digest":null}
                    ]}]
                    """
                            .formatted(archive, base));
            answers.put(base + archive + ".sha256", SHA256 + "\n");

            assertThat(tools.graalvm(21).digest())
                    .as("every JDK 21 and 17 release is such a one")
                    .isEqualTo(SHA256);
        }

        /** Harm prevented: one odd address in GitHub's answer ends the lookup in a crash. */
        @Test
        void aDigestFileAtNoAddressMakesGraalvmUnknown() {
            String archive = "graalvm-community-jdk-21.0.2_linux-x64_bin.tar.gz";
            answers.put(
                    "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases?per_page=100",
                    """
                    [{"prerelease":false,"draft":false,"assets":[
                      {"name":"%1$s","browser_download_url":"https://github.com/%1$s","digest":null},
                      {"name":"%1$s.sha256","browser_download_url":"https://github.com/a b","digest":null}
                    ]}]
                    """
                            .formatted(archive));

            assertThatThrownBy(() -> tools.graalvm(21))
                    .as("said as a vendor's failure")
                    .isInstanceOf(ToolUnavailable.class);
        }

        @Test
        void aDigestOfAnotherKindIsRefused() {
            answers.put(
                    "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases?per_page=100",
                    """
                    [{"prerelease":false,"draft":false,"assets":[
                      {"name":"graalvm-community-jdk-25.0.2_linux-x64_bin.tar.gz",
                       "browser_download_url":"https://github.com/a.tgz","digest":"md5:%s"}]}]
                    """
                            .formatted(SHA256));

            assertThatThrownBy(() -> tools.graalvm(25))
                    .as("the digest says what it is")
                    .isInstanceOf(ToolUnavailable.class);
        }

        @Test
        void anAbsurdMajorInANameIsPassedOver() {
            answers.put(
                    "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases?per_page=100",
                    """
                    [{"prerelease":false,"draft":false,"assets":[
                      {"name":"graalvm-community-jdk-99999999999999_linux-x64_bin.tar.gz",
                       "browser_download_url":"https://github.com/a.tgz","digest":null}]}]
                    """);

            assertThatThrownBy(() -> tools.graalvm(25))
                    .as("said as none found, never a crash")
                    .isInstanceOf(ToolUnavailable.class);
        }

        private static final String RELEASES =
                "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases?per_page=100";

        private String release(boolean prerelease, String asset) {
            return """
                    {"prerelease":%s,"draft":false,"assets":[
                      {"name":"%s.sha256","browser_download_url":"https://github.com/x","digest":null},
                      {"name":"%s","browser_download_url":"https://github.com/%s",
                       "digest":"sha256:%s"}]}
                    """
                    .formatted(prerelease, asset, asset, asset, SHA256);
        }

        @Test
        void theNewerNameIsReadForItsMajorAndVersion() {
            answers.put(
                    RELEASES,
                    "["
                            + release(
                                    false,
                                    "graalvm-community-jdk-25i5-25.0.5.0.3_linux-x64_bin.tar.gz")
                            + "]");

            assertThat(tools.graalvm(25))
                    .as("a filter on jdk-25 would stop at 25.0.2")
                    .satisfies(
                            g -> {
                                assertThat(g.version()).as("version").isEqualTo("25.0.5.0.3");
                                assertThat(g.digest()).as("no algorithm's name").isEqualTo(SHA256);
                            });
        }

        @Test
        void theOlderNameIsReadToo() {
            answers.put(
                    RELEASES,
                    "["
                            + release(false, "graalvm-community-jdk-25.0.2_linux-x64_bin.tar.gz")
                            + "]");

            assertThat(tools.graalvm(25).version()).as("before the split").isEqualTo("25.0.2");
        }

        @Test
        void aPrereleaseAndAnotherMajorAreLeftOut() {
            answers.put(
                    RELEASES,
                    "["
                            + release(
                                    true, "graalvm-community-jdk-25i6-25.0.6_linux-x64_bin.tar.gz")
                            + ","
                            + release(
                                    false, "graalvm-community-jdk-26i1-26.0.1_linux-x64_bin.tar.gz")
                            + ","
                            + release(
                                    false, "graalvm-community-jdk-25i5-25.0.5_linux-x64_bin.tar.gz")
                            + "]");

            assertThat(tools.graalvm(25).version())
                    .as("the newest stable of this major")
                    .isEqualTo("25.0.5");
        }

        @Test
        void aRateLimitsRefusalLeavesItUnavailable() {
            answers.put(RELEASES, "{\"message\":\"API rate limit exceeded\"}");

            assertThatThrownBy(() -> tools.graalvm(25))
                    .as("60 requests an hour from a shared address")
                    .isInstanceOf(ToolUnavailable.class);
        }
    }

    /** Harm prevented: a vendor's whole answer goes unread, or half of a doubled one is taken. */
    @Nested
    class VendorAnswers {
        private static final String RELEASES =
                "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases?per_page=100";

        @Test
        void anAnswerLongerThanSnakeYamlsDefaultIsReadWhole() {
            String asset = "graalvm-community-jdk-25i5-25.0.5.0.3_linux-x64_bin.tar.gz";
            String filler =
                    "{\"prerelease\":true,\"draft\":false,\"assets\":[],\"body\":\""
                            + "x".repeat(1024 * 1024)
                            + "\"},";
            answers.put(
                    RELEASES,
                    "["
                            + filler.repeat(4)
                            + """
                            {"prerelease":false,"draft":false,"assets":[
                              {"name":"%s","browser_download_url":"https://github.com/a",
                               "digest":"sha256:%s"}]}]
                            """
                                    .formatted(asset, SHA256));

            assertThat(tools.graalvm(25).version())
                    .as("GitHub's list of GraalVM's releases was 5.4 MB, SnakeYAML's default 3")
                    .isEqualTo("25.0.5.0.3");
        }

        @Test
        void aKeyNamedTwiceIsRefused() {
            String link = "https://github.com/adoptium/temurin21-binaries/jdk.tar.gz";
            temurinAnswers(
                    21,
                    """
                    [{"binary":{"package":{"checksum":"%1$s","link":"%2$s","link":"%2$s.x"}},
                      "version":{"semver":"21.0.13+9.0.LTS"}}]
                    """
                            .formatted(SHA256, link));

            assertThatThrownBy(() -> tools.temurin(21))
                    .as("which of the two a reader takes is no answer")
                    .isInstanceOf(ToolUnavailable.class);
        }

        @ParameterizedTest
        @CsvSource(
                delimiter = '|',
                value = {
                    "[{\"binary\": !!java.io.File \"/etc\"}]   | a tag naming a Java class",
                    "[{\"binary\": {\"package\": \"a\\/b\"}}]   | an escape YAML 1.1 lacks",
                    "[1] x                                     | trailing text",
                    "[99999999999999999999:00]                 | a base-60 number too large",
                    "[!!int \"x\"]                              | a number tag on no number",
                    "[!!binary \"%%%\"]                         | a binary tag on no base64",
                    "{ &a { [*a]: 1 }: 2 }                     | a key that holds itself",
                })
        void whatIsNoPlainJsonLeavesItUnavailable(String answer, String what) {
            temurinAnswers(21, answer);

            assertThatThrownBy(() -> tools.temurin(21))
                    .as(what)
                    .isInstanceOf(ToolUnavailable.class)
                    .hasMessage("api.adoptium.net answered no JSON");
        }
    }

    /** Harm prevented: a release candidate is installed, or a pom's range is not met. */
    @Nested
    class MavenFaults {
        @Test
        void anEmptyVersionInTheListIsPassedOver() {
            answers.put(
                    DIST + "maven-metadata.xml",
                    "<metadata><versioning><versions><version/><version>3.9.16</version>"
                            + "</versions></versioning></metadata>");
            answers.put(DIST + "3.9.16/apache-maven-3.9.16-bin.tar.gz.sha512", SHA512);

            assertThat(tools.maven("").version()).as("no crash on it").isEqualTo("3.9.16");
        }

        @Test
        void whatIsNoVersionNorRangeIsRefused() {
            answers.put(
                    DIST + "maven-metadata.xml",
                    "<metadata><versioning><versions><version>3.9.16</version>"
                            + "</versions></versioning></metadata>");
            answers.put(DIST + "3.9.16/apache-maven-3.9.16-bin.tar.gz.sha512", SHA512);

            assertThatThrownBy(() -> tools.maven("3.8.7],[3.9.0"))
                    .as("never rewritten into a range nobody wrote")
                    .isInstanceOf(ToolUnavailable.class);
        }
    }

    @Nested
    class Maven {
        private static final String METADATA =
                """
                <metadata><versioning><latest>4.0.0-rc-7</latest><release>4.0.0-rc-7</release>
                <versions><version>3.8.7</version><version>3.9.16</version>
                <version>3.10.0-rc-1</version><version>3.10.0</version>
                <version>4.0.0-rc-7</version></versions></versioning></metadata>
                """;

        private void known() {
            answers.put(DIST + "maven-metadata.xml", METADATA);
            for (String v : new String[] {"3.8.7", "3.9.16", "3.10.0"}) {
                answers.put(
                        DIST + v + "/apache-maven-" + v + "-bin.tar.gz.sha512",
                        SHA512 + "  apache-maven-" + v + "-bin.tar.gz\n");
            }
        }

        @ParameterizedTest
        @CsvSource({
            "'', 3.10.0",
            "3.9, 3.10.0",
            "'[3.9,)', 3.10.0",
            "'[3.8,3.10)', 3.9.16",
            "'[3.8.7]', 3.8.7"
        })
        void theNewestStableInTheRangeIsTaken(String asked, String version) {
            known();

            assertThat(tools.maven(asked))
                    .as("never what the source calls its release, which is a candidate")
                    .isEqualTo(
                            new ToolDownload(
                                    version,
                                    DIST + version + "/apache-maven-" + version + "-bin.tar.gz",
                                    SHA512));
        }

        @ParameterizedTest
        @CsvSource({"'[5,)'", "'[nonsense'"})
        void aRangeNothingStableMeetsIsUnavailable(String asked) {
            known();

            assertThatThrownBy(() -> tools.maven(asked))
                    .as("it names what was asked")
                    .isInstanceOf(ToolUnavailable.class);
        }

        @Test
        void aMissingDigestLeavesItUnavailable() {
            answers.put(DIST + "maven-metadata.xml", METADATA);

            assertThatThrownBy(() -> tools.maven("[3.9,)"))
                    .as("an archive that cannot be checked is not fetched")
                    .isInstanceOf(ToolUnavailable.class);
        }

        @Test
        void aBrokenListLeavesItUnavailable() {
            answers.put(DIST + "maven-metadata.xml", "<metadata><broken");

            assertThatThrownBy(() -> tools.maven(""))
                    .as("a broken answer is not a crash")
                    .isInstanceOf(ToolUnavailable.class);
        }
    }

    /** Harm prevented: a mirror's host lets an archive from any other host into the image. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://other.example.org/maven/apache-maven-3.9.16-bin.tar.gz",
                "http://repo.example.org/maven/apache-maven-3.9.16-bin.tar.gz",
                "https://repo.example.org.other.example/apache-maven-3.9.16-bin.tar.gz"
            })
    void onlyTheMirrorsOwnHostByHttpsIsTaken(String url) {
        assertThatThrownBy(() -> new ToolDownload("3.9.16", url, SHA512, "repo.example.org"))
                .isInstanceOf(ToolUnavailable.class);
    }
}
