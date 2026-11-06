package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.ecosystem.api.Finding;
import org.junit.jupiter.api.Test;

/** Harm prevented: a project gets a distribution's version, or no tool at all. */
class ToolResolverTest {
    private static final String SHA256 = "a".repeat(64);
    private static final String DIST =
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/";

    private final Map<String, String> answers = new HashMap<>();
    private final List<Finding> asked = new ArrayList<>();
    private final ToolResolver resolver =
            new ToolResolver(
                    new ToolDownloads(
                            uri -> {
                                String answer = answers.get(uri.toString());
                                if (answer == null) {
                                    throw new IOException("offline");
                                }
                                return answer;
                            }));

    private static Finding finding(String key, String value, boolean unknown) {
        return new Finding(
                Finding.Part.TOOLS,
                key,
                value,
                "pom.xml",
                7,
                Finding.Origin.CHECKOUT,
                unknown,
                "what Detect said");
    }

    private void temurin(int major) {
        answers.put(
                "https://api.adoptium.net/v3/assets/latest/"
                        + major
                        + "/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse",
                """
                [{"binary":{"package":{"checksum":"%s","link":"https://github.com/j%d.tgz"}},
                  "version":{"semver":"%d.0.5+8"}}]
                """
                        .formatted(SHA256, major, major));
    }

    private void maven() {
        answers.put(
                DIST + "maven-metadata.xml",
                "<metadata><versioning><versions><version>3.9.16</version>"
                        + "<version>4.0.0-rc-7</version></versions></versioning></metadata>");
        answers.put(
                DIST + "3.9.16/apache-maven-3.9.16-bin.tar.gz.sha512", "b".repeat(128) + "  x\n");
    }

    private void graalvm(int major) {
        answers.put(
                "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases?per_page=100",
                """
                [{"prerelease":false,"draft":false,"assets":[
                  {"name":"graalvm-community-jdk-%di5-%d.0.5_linux-x64_bin.tar.gz",
                   "browser_download_url":"https://github.com/g.tgz","digest":"sha256:%s"}]}]
                """
                        .formatted(major, major, SHA256));
    }

    private Finding only(List<Finding> all, String key) {
        assertThat(all)
                .as("one finding under " + key)
                .filteredOn(f -> f.key().equals(key))
                .hasSize(1);
        return all.stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void theJdkPackageGivesWayToItsExactLinesAndPaths() {
        asked.add(finding("java.version", "21", false));
        asked.add(finding("package.openjdk-21-jdk-headless", "openjdk-21-jdk-headless", false));
        temurin(21);

        List<Finding> all = resolver.resolve(asked);

        assertThat(all)
                .as("the package would be a second JDK")
                .noneMatch(f -> f.key().startsWith("package."));
        assertThat(only(all, "run.1-java").value())
                .as("the vendor's own")
                .contains("https://github.com/j21.tgz");
        assertThat(only(all, "run.1-java").note()).as("names what it fetched").contains("21.0.5+8");
        assertThat(only(all, "env.JAVA_HOME").value())
                .as("a build's plugin may need it")
                .isEqualTo("/opt/java");
        assertThat(only(all, "env.JDK_HOME").value())
                .as("what else may read")
                .isEqualTo("/opt/java");
        assertThat(only(all, "env.JRE_HOME").value())
                .as("what else may read")
                .isEqualTo("/opt/java");
        assertThat(only(all, "java.version").value()).as("what was asked stays").isEqualTo("21");
    }

    @Test
    void aJavaNobodyAskedForIsStillAJdk() {
        asked.add(finding("package.default-jdk-headless", "default-jdk-headless", false));
        temurin(25);

        assertThat(only(resolver.resolve(asked), "run.1-java").value())
                .as("Maven runs on a JDK")
                .contains("j25.tgz");
    }

    @Test
    void aVendorsFailureKeepsThePackageAndSaysWhatWasAsked() {
        asked.add(finding("java.version", "21", false));
        asked.add(finding("package.openjdk-21-jdk-headless", "openjdk-21-jdk-headless", false));

        Finding kept = only(resolver.resolve(asked), "package.openjdk-21-jdk-headless");

        assertThat(kept.value())
                .as("a distribution's JDK beats none")
                .isEqualTo("openjdk-21-jdk-headless");
        assertThat(kept.note())
                .as("what was asked")
                .contains("Java 21")
                .contains("what Detect said");
    }

    @Test
    void limitsNoJdkMeetsFetchNoGraalvmEither() {
        asked.add(finding("java.version", "", true));
        asked.add(finding("package.openjdk-21-jdk-headless", "", true));
        asked.add(finding("graalvm.version", "", false));
        graalvm(25);

        assertThat(resolver.resolve(asked))
                .as("no JDK is known to take a GraalVM for")
                .noneMatch(f -> f.key().equals("run.2-graalvm"));
    }

    @Test
    void aJavaThatIsNoMajorFetchesNothingForIt() {
        asked.add(finding("java.version", "1.8", false));
        asked.add(finding("package.openjdk-8-jdk-headless", "openjdk-8-jdk-headless", false));
        temurin(25);

        assertThat(resolver.resolve(asked))
                .as("never a JDK it did not ask, and no crash")
                .isEqualTo(asked);
    }

    @Test
    void aGraalvmNotFoundIsSaidAsUnknown() {
        asked.add(finding("java.version", "21", false));
        asked.add(finding("package.openjdk-21-jdk-headless", "x", false));
        asked.add(finding("graalvm.version", "", false));
        temurin(21);

        Finding kept = only(resolver.resolve(asked), "graalvm.version");

        assertThat(kept.unknown()).as("no package stands in for it").isTrue();
        assertThat(kept.note()).as("what was asked").contains("GraalVM for Java 21");
    }

    @Test
    void aMavenWithNoRangeReadsPlainlyWhenNotFound() {
        asked.add(finding("java.version", "21", false));
        asked.add(finding("package.openjdk-21-jdk-headless", "x", false));
        asked.add(finding("package.maven", "maven", false));
        temurin(21);

        assertThat(only(resolver.resolve(asked), "package.maven").note())
                .as("what was asked")
                .contains("; Maven was asked");
    }

    @Test
    void limitsNoJdkMeetsFetchNothing() {
        asked.add(finding("java.version", "", true));
        asked.add(finding("package.openjdk-21-jdk-headless", "", true));
        temurin(25);

        assertThat(resolver.resolve(asked)).as("what Detect left unknown stays").isEqualTo(asked);
    }

    @Test
    void mavenWithoutAWrapperIsFetchedInTheRange() {
        asked.add(finding("maven.version", "[3.9,)", false));
        asked.add(finding("package.maven", "maven", false));
        maven();

        List<Finding> all = resolver.resolve(asked);

        assertThat(all)
                .as("3.8.7 would stop the build")
                .noneMatch(f -> f.key().equals("package.maven"));
        assertThat(only(all, "run.3-maven").value())
                .as("the stable one")
                .contains("3.9.16")
                .doesNotContain("rc");
        assertThat(only(all, "env.MAVEN_HOME").value()).as("its home").isEqualTo("/opt/maven");
    }

    @Test
    void aWrapperKeepsItsPriorityForMaven() {
        asked.add(finding("maven.version", "3.9.15", false));
        maven();

        assertThat(resolver.resolve(asked))
                .as("the wrapper fetches its own")
                .noneMatch(f -> f.key().equals("run.3-maven"));
    }

    @Test
    void oneVendorsFailureCostsOnlyItsTool() {
        asked.add(finding("java.version", "21", false));
        asked.add(finding("package.openjdk-21-jdk-headless", "x", false));
        asked.add(finding("package.maven", "maven", false));
        asked.add(finding("graalvm.version", "", false));
        temurin(21);
        maven();

        List<Finding> all = resolver.resolve(asked);

        assertThat(all)
                .as("GitHub's limit leaves Java and Maven exact")
                .anyMatch(f -> f.key().equals("run.1-java"))
                .anyMatch(f -> f.key().equals("run.3-maven"))
                .noneMatch(f -> f.key().equals("run.2-graalvm"));
        assertThat(only(all, "graalvm.version").note()).as("said").contains("GraalVM for Java 21");
    }

    @Test
    void aNativeBuildGetsGraalvmBesideItsJdk() {
        asked.add(finding("java.version", "25", false));
        asked.add(finding("package.openjdk-25-jdk-headless", "x", false));
        asked.add(finding("graalvm.version", "", false));
        temurin(25);
        graalvm(25);

        List<Finding> all = resolver.resolve(asked);

        assertThat(only(all, "run.2-graalvm").value())
                .as("only what it adds goes on the path")
                .contains("[ -e /usr/local/bin/");
        assertThat(only(all, "env.GRAALVM_HOME").value()).as("its home").isEqualTo("/opt/graalvm");
        assertThat(only(all, "env.JAVA_HOME").value())
                .as("the project's JDK stays")
                .isEqualTo("/opt/java");
    }

    /** Harm prevented: GraalVM's java stands on the path before the distribution's JDK. */
    @Test
    void noGraalvmIsFetchedBesideAJdkThatWasNot() {
        asked.add(finding("java.version", "21", false));
        asked.add(finding("package.openjdk-21-jdk-headless", "openjdk-21-jdk-headless", false));
        asked.add(finding("graalvm.version", "", false));
        graalvm(21);

        List<Finding> all = resolver.resolve(asked);

        assertThat(all)
                .as("nothing would be there for it to stand beside")
                .noneMatch(f -> f.key().startsWith("run."));
        assertThat(only(all, "graalvm.version").unknown()).as("left to the person").isTrue();
        assertThat(only(all, "graalvm.version").note()).as("said").contains("GraalVM for Java 21");
    }

    @Test
    void theLinesStandWhereTheToolWasAsked() {
        asked.add(finding("package.maven", "maven", false));
        maven();

        Finding line = only(resolver.resolve(asked), "run.3-maven");

        assertThat(line.file()).as("a person looks there").isEqualTo("pom.xml");
        assertThat(line.line()).as("its line").isEqualTo(7);
        assertThat(line.unknown()).as("known").isFalse();
    }

    /** Harm prevented: GraalVM's own java stands on the path in place of the project's JDK. */
    @Test
    void theJdksLinesComeBeforeGraalvmsInTheOrderPoreloJoinsThem() {
        asked.add(finding("java.version", "25", false));
        asked.add(finding("package.openjdk-25-jdk-headless", "x", false));
        asked.add(finding("graalvm.version", "", false));
        asked.add(finding("package.maven", "maven", false));
        temurin(25);
        graalvm(25);
        maven();

        // Porelo joins run.<name> in the order of the names.
        List<String> joined =
                resolver.resolve(asked).stream()
                        .filter(f -> f.key().startsWith("run."))
                        .sorted(java.util.Comparator.comparing(Finding::key))
                        .map(Finding::value)
                        .toList();

        assertThat(joined).as("each tool's lines").hasSize(3);
        assertThat(joined.get(0)).as("the JDK links its commands first").contains("/opt/java");
        assertThat(joined.get(1))
                .as("GraalVM then links only what the JDK has not")
                .contains("/opt/graalvm");
        assertThat(joined.get(2)).as("Maven's, in any place").contains("/opt/maven");
    }
}
