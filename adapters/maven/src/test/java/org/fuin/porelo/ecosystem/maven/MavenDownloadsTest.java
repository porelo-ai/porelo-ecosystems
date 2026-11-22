package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.fuin.porelo.ecosystem.shared.ToolDownload;
import org.fuin.porelo.ecosystem.shared.ToolUnavailable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Answers shaped as Maven Central's were measured. */
class MavenDownloadsTest {
    private static final String SHA512 = "a".repeat(128);
    private static final String DIST =
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/";

    private final Map<String, String> answers = new HashMap<>();
    private final MavenDownloads tools =
            new MavenDownloads(
                    uri -> {
                        String answer = answers.get(uri.toString());
                        if (answer == null) {
                            throw new IOException("no such answer: " + uri);
                        }
                        return answer;
                    });

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
}
