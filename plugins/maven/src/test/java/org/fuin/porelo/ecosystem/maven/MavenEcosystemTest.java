package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.fuin.porelo.ecosystem.maven.Fixture.project;
import static org.fuin.porelo.ecosystem.maven.Fixture.properties;
import static org.fuin.porelo.ecosystem.maven.Fixture.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.fuin.porelo.ecosystem.api.Detection;
import org.fuin.porelo.ecosystem.api.EcosystemRefused;
import org.fuin.porelo.ecosystem.api.Extension;
import org.fuin.porelo.ecosystem.api.File;
import org.fuin.porelo.ecosystem.api.Finding;
import org.fuin.porelo.ecosystem.api.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MavenEcosystemTest {
    @TempDir Path root;
    Fixture f;

    @BeforeEach
    void fixture() throws IOException {
        f = new Fixture(root);
    }

    private Detection detect() {
        return new MavenEcosystem().detect(f.checkout, f.home);
    }

    private Finding only(String key) {
        List<Finding> found =
                detect().findings().stream().filter(x -> x.key().equals(key)).toList();
        assertThat(found).as("one finding under %s in %s", key, found).hasSize(1);
        return found.get(0);
    }

    private static String plugin(String group, String artifact, String body) {
        return """
                  <build><plugins><plugin>
                    <groupId>%s</groupId><artifactId>%s</artifactId>
                    %s
                  </plugin></plugins></build>
                """
                .formatted(group, artifact, body);
    }

    /** Harm prevented: a person cannot tell which build of the plugin answered. */
    @Test
    void theVersionIsTheOneThePackageIsBuiltAs() {
        assertThat(new MavenEcosystem().version())
                .as("the package version this build makes, never one written by hand")
                .isNotBlank()
                .isEqualTo(System.getProperty("test.package.version"));
    }

    /** Harm prevented: Porelo offers a plugin for a directory that holds nothing of its tool. */
    @Test
    void aDirectoryWithoutAPomIsNotRecognized() {
        assertThatThrownBy(this::detect)
                .as("the contract tells this apart from an empty answer")
                .isInstanceOfSatisfying(
                        EcosystemRefused.class,
                        e ->
                                assertThat(e.reason())
                                        .isEqualTo(EcosystemRefused.Reason.NOT_RECOGNIZED));
    }

    /**
     * Harm prevented: settings that do not parse either stop the whole answer, or are taken for
     * settings naming nothing, or leak what they hold into the answer.
     */
    @Test
    void settingsThatDoNotParseAreWarnedOfAndTheRestIsAnswered() throws IOException {
        f.pom("pom.xml", project("child", repository("corp", "https://nexus.example.org/m")))
                .settings(
                        """
                        <settings>
                          <mirrors><mirror><id>hidden</id><mirrorOf>*</mirrorOf><url>https://hidden.example.org/m</url></mirror></mirrors>
                          <servers xmlns=http://maven.apache.org/SETTINGS/1.1.0>
                        </settings>
                        """);

        Detection detection = detect();

        Finding warning = only("unknown..m2/settings.xml");
        assertThat(warning.unknown()).as("a warning, not a value").isTrue();
        assertThat(warning.note()).as("where to look").contains("line 3");
        assertThat(detection.findings())
                .as("the rest of the checkout is still answered")
                .anyMatch(x -> x.key().equals("host.nexus.example.org"));
        assertThat(detection.findings())
                .as("nothing the unread file holds reaches the answer")
                .noneMatch(x -> (x.key() + x.value() + x.note()).contains("hidden"));
    }

    /** Harm prevented: the image holds another JDK than the one the build compiles for. */
    @Nested
    class TheJdk {
        @ParameterizedTest
        @CsvSource({
            "maven.compiler.release, 25, 25",
            "maven.compiler.target, 17, 17",
            "maven.compiler.source, 1.8, 8",
            "java.version, 21, 21"
        })
        void isReadFromTheProperties(String name, String written, String answered)
                throws IOException {
            f.pom("pom.xml", project("child", properties(name, written)));

            Finding java = only("java.version");

            assertThat(java.value()).as("as a major version").isEqualTo(answered);
            assertThat(java.part()).isEqualTo(Finding.Part.TOOLS);
            assertThat(java.file()).as("the file a person opens").isEqualTo("pom.xml");
            assertThat(java.line()).as("the property's line").isPositive();
            assertThat(java.origin()).isEqualTo(Finding.Origin.CHECKOUT);
        }

        @Test
        void theCompilersReleaseWinsOverAProperty() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("maven.compiler.release", "25")
                                    + plugin(
                                            "org.apache.maven.plugins",
                                            "maven-compiler-plugin",
                                            "<configuration><release>11</release></configuration>")));

            assertThat(only("java.version").value())
                    .as("the compiler is told this one")
                    .isEqualTo("11");
        }

        @Test
        void aCompilerManagedInAParentStillCounts() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                              <build><pluginManagement><plugins><plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <configuration><release>21</release></configuration>
                              </plugin></plugins></pluginManagement></build>
                            """));

            assertThat(only("java.version").value())
                    .as("every jar build uses the compiler without listing it")
                    .isEqualTo("21");
        }

        @Test
        void theHighestOfAllModulesIsAnswered() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "root",
                                    properties("maven.compiler.release", "17")
                                            + "<modules><module>a</module></modules>"))
                    .pom("a/pom.xml", project("a", properties("maven.compiler.release", "25")));

            assertThat(only("java.version").value())
                    .as("only that JDK builds both")
                    .isEqualTo("25");
        }

        @Test
        void anEnforcerOnlyManagedAsksNothing() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                              <build><pluginManagement><plugins><plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-enforcer-plugin</artifactId>
                                <configuration><rules><requireJavaVersion>
                                  <version>[17,)</version>
                                </requireJavaVersion></rules></configuration>
                              </plugin></plugins></pluginManagement></build>
                            """));

            assertThat(detect().findings())
                    .as("no build runs it, unlike the compiler")
                    .noneMatch(x -> x.key().equals("java.version"));
        }

        @Test
        void onlyAnEnforcerRuleIsReadForItsLowest() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    "maven-enforcer-plugin",
                                    """
                                    <configuration><rules><requireJavaVersion>
                                      <version>[17,)</version>
                                    </requireJavaVersion></rules></configuration>
                                    """)));

            assertThat(only("java.version").value()).as("the range's start").isEqualTo("17");
        }

        @Test
        void aVersionFromTheMachineIsUnknownAndNamed() throws IOException {
            f.pom("pom.xml", project("child", properties("maven.compiler.release", "${env.JDK}")));

            Finding java = only("java.version");

            assertThat(java.unknown()).as("never guessed").isTrue();
            assertThat(java.value()).isEmpty();
            assertThat(java.note()).as("what to look at").contains("${env.JDK}");
        }

        @Test
        void nothingNamedAnswersNothing() throws IOException {
            f.pom("pom.xml", project("child", ""));

            assertThat(detect().findings())
                    .as("no version is made up")
                    .noneMatch(x -> x.key().equals("java.version"));
        }
    }

    /** Harm prevented: the image holds a Maven too old for the build, or fetches one unasked. */
    @Nested
    class Maven {
        private static final String WRAPPER = ".mvn/wrapper/maven-wrapper.properties";

        @Test
        void theWrappersVersionIsAnsweredAndItsHostIsAHost() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .pom(
                            WRAPPER,
                            "# pinned\ndistributionUrl=https\\://dl.example.org/apache-maven-3.9.15-bin.zip\n");

            Finding maven = only("maven.version");
            Finding host = only("host.dl.example.org");

            assertThat(maven.value()).isEqualTo("3.9.15");
            assertThat(maven.file()).isEqualTo(WRAPPER);
            assertThat(maven.line()).as("the line, past the comment").isEqualTo(2);
            assertThat(host.part()).as("the build goes there first").isEqualTo(Finding.Part.RULES);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "distributionUrl = https\\://dl.example.org/apache-maven-3.9.15-bin.zip\n",
                    "  distributionUrl: https\\://dl.example.org/apache-maven-3.9.15-bin.zip\n",
                    "distributionUrl=https\\://dl.example.org/\\\n    apache-maven-3.9.15-bin.zip\n"
                })
        void theWrapperIsReadInEveryFormItsPropertiesAllow(String written) throws IOException {
            f.pom("pom.xml", project("child", "")).pom(WRAPPER, "# pinned\n! too\n" + written);

            assertThat(only("maven.version").value())
                    .as("the wrapper reads this file as properties")
                    .isEqualTo("3.9.15");
            assertThat(only("maven.version").line()).as("where the URL starts").isEqualTo(3);
            assertThat(only("host.dl.example.org").line()).isEqualTo(3);
        }

        @Test
        void anEnforcerRangeIsAnsweredAsWritten() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    "maven-enforcer-plugin",
                                    """
                                    <executions><execution><configuration><rules><requireMavenVersion>
                                      <version>[3.9,)</version>
                                    </requireMavenVersion></rules></configuration></execution></executions>
                                    """)));

            assertThat(only("maven.version").value()).isEqualTo("[3.9,)");
        }

        @Test
        void aPomAskingNothingStillNeedsAMaven() throws IOException {
            f.pom("pom.xml", project("child", ""));

            Finding maven = only("maven.version");

            assertThat(maven.value()).as("any").isEmpty();
            assertThat(maven.unknown()).as("nothing hides it: nothing asks").isFalse();
            assertThat(maven.note()).contains("any Maven");
        }
    }

    /** Harm prevented: the build fails in its box on a program the image lacks. */
    @Nested
    class SystemPackages {
        @Test
        void aPluginThatRunsAProgramNamesItsPackage() throws IOException {
            f.pom(
                    "pom.xml",
                    project("child", plugin("org.graalvm.buildtools", "native-maven-plugin", "")));

            assertThat(only("package.gcc").value()).isEqualTo("gcc");
            assertThat(only("package.libc6-dev").value())
                    .as("native-image links against it")
                    .isEqualTo("libc6-dev");
            assertThat(only("graalvm.version").line())
                    .as("Resolve fetches GraalVM for this, naming the plugin")
                    .isPositive();
            assertThat(only("package.zlib1g-dev").line()).as("the plugin's line").isPositive();
        }

        @Test
        void aNativeBuildInTwoModulesStandsWhereItsPackagesDo() throws IOException {
            String plugin = plugin("org.graalvm.buildtools", "native-maven-plugin", "");
            f.pom(
                            "pom.xml",
                            project(
                                    "root",
                                    "<modules><module>a</module><module>b</module></modules>"))
                    .pom("a/pom.xml", project("a", plugin))
                    .pom("b/pom.xml", project("b", plugin));

            assertThat(only("graalvm.version").file())
                    .as("one tool, one place, as its packages")
                    .isEqualTo(only("package.gcc").file());
        }

        @Test
        void aPluginOnlyManagedNeedsNothing() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                              <build><pluginManagement><plugins><plugin>
                                <groupId>org.graalvm.buildtools</groupId>
                                <artifactId>native-maven-plugin</artifactId>
                              </plugin></plugins></pluginManagement></build>
                            """));

            assertThat(detect().findings())
                    .as("no build runs it")
                    .noneMatch(x -> x.key().equals("package.gcc"));
        }

        @Test
        void whatExecRunsIsNamedAndOwnScriptsAreNot() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.codehaus.mojo",
                                    "exec-maven-plugin",
                                    """
                                    <executions>
                                      <execution><id>a</id><configuration><executable>/usr/bin/protoc</executable></configuration></execution>
                                      <execution><id>b</id><configuration><executable>./gen.sh</executable></configuration></execution>
                                      <execution><id>d</id><configuration><executable>${java.home}/bin/java</executable></configuration></execution>
                                      <execution><id>c</id><configuration><executable>${tool.path}</executable></configuration></execution>
                                    </executions>
                                    """)));

            List<Finding> packages =
                    detect().findings().stream()
                            .filter(x -> x.key().startsWith("package."))
                            .toList();

            assertThat(packages)
                    .extracting(Finding::key)
                    .as("neither its own script nor the JDK's java needs a package")
                    .containsExactlyInAnyOrder(
                            "package.protoc",
                            "package.${tool.path}",
                            "package.default-jdk-headless",
                            "package.maven");
            assertThat(only("package.${tool.path}").unknown()).as("never guessed").isTrue();
        }
    }

    /** Harm prevented: the image has no JDK or Maven, so no build can start in a task. */
    @Nested
    class WhatTheImageInstalls {
        private static final String WRAPPER = ".mvn/wrapper/maven-wrapper.properties";
        private static final String ZIP =
                "distributionUrl=https\\://dl.example.org/apache-maven-3.9.15-bin.zip\n";
        private static final String ONLY_SCRIPT = "distributionType=only-script\n";
        private static final String DIGEST = "distributionSha256Sum=abc\n";

        @ParameterizedTest
        @CsvSource({"25, 25", "21, 21", "17, 17", "11, 11", "1.8, 8", "22, 25", "9, 11", "1.5, 8"})
        void theJdkIsTheLowestPackagedOneThatCompilesTheRelease(String written, String packaged)
                throws IOException {
            f.pom("pom.xml", project("child", properties("maven.compiler.release", written)));

            String key = "package.openjdk-" + packaged + "-jdk-headless";

            assertThat(only(key).value())
                    .as("the name apt installs")
                    .isEqualTo(key.substring("package.".length()));
            assertThat(only(key).part()).isEqualTo(Finding.Part.TOOLS);
            assertThat(only(key).line()).as("where the release is asked").isPositive();
            assertThat(only(key).unknown()).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
            "17, 25, 25",
            "17, '[17,21)', 17",
            "17, '[21,25)', 21",
            "11, '[11,17)', 11",
            "11, '(,8],[21,)', 21",
            "8, '[11,16]', 11",
            "17, '(17,)', 17"
        })
        void theJdkMeetsTheCompilersLevelAndTheEnforcersRangeTogether(
                String release, String range, String packaged) throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("maven.compiler.release", release) + requireJava(range)));

            assertThat(detect().findings())
                    .as("a build that needs more than its bytecode's level stops at the enforcer")
                    .filteredOn(x -> x.key().contains("-jdk"))
                    .singleElement()
                    .satisfies(
                            x -> {
                                assertThat(x.key())
                                        .isEqualTo("package.openjdk-" + packaged + "-jdk-headless");
                                assertThat(x.unknown()).as("it is packaged").isFalse();
                            });
        }

        @Test
        void theEnforcersFloorAboveTheReleaseIsTheJavaAskedFor() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("maven.compiler.release", "17") + requireJava("25")));

            assertThat(only("java.version").value())
                    .as("the build asks for this JDK, the release only for its bytecode")
                    .isEqualTo("25");
        }

        @ParameterizedTest
        @CsvSource({
            "21, '[11,17)', 21",
            "8, '[18,20]', 18",
            "8, '(,7]', 8",
            "21, '[17,21.0)', 21",
            "21, '[17,21.0.0)', 21",
            "21, '[21]', 21",
            "25, '[21,25]', 25",
            "25, '[21,25.0.1)', 25",
            "8, '(,1.8.0)', 8"
        })
        void limitsNoPackagedJdkMeetsAreUnknownAndNeverGuessed(
                String release, String range, String named) throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("maven.compiler.release", release) + requireJava(range)));

            assertThat(only("package.openjdk-" + named + "-jdk-headless").unknown())
                    .as("a JDK outside the range stops the build, however it is chosen")
                    .isTrue();
        }

        @Test
        void limitsThatContradictEachOtherAnswerNoJavaAsKnown() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("maven.compiler.release", "21") + requireJava("[11,17)")));

            assertThat(only("java.version").unknown())
                    .as("no JDK at all meets both, so none may read as asked for")
                    .isTrue();
        }

        @Test
        void aRangeWithAGapAnswersTheJavaItReallyAllows() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("maven.compiler.release", "11")
                                    + requireJava("(,8],[21,)")));

            assertThat(only("java.version").value()).as("11 falls in the gap").isEqualTo("21");
        }

        @Test
        void aRuleOnlyAllowingBelowThePackagedOnesInstallsNoneOfThem() throws IOException {
            f.pom("pom.xml", project("child", requireJava("(,7]")));

            assertThat(detect().findings())
                    .as("8 is what the rule forbids")
                    .filteredOn(x -> x.key().contains("-jdk"))
                    .singleElement()
                    .satisfies(x -> assertThat(x.unknown()).isTrue());
        }

        @Test
        void aRuleTheBuildOverridesCountsAsItsOverride() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                            <build>
                              <pluginManagement><plugins><plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-enforcer-plugin</artifactId>
                                <configuration><rules><requireJavaVersion>
                                  <version>[17,18)</version>
                                </requireJavaVersion></rules></configuration>
                              </plugin></plugins></pluginManagement>
                              <plugins><plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-enforcer-plugin</artifactId>
                                <configuration><rules><requireJavaVersion>
                                  <version>21</version>
                                </requireJavaVersion></rules></configuration>
                              </plugin></plugins>
                            </build>
                            """));

            assertThat(only("package.openjdk-21-jdk-headless").unknown())
                    .as("only the rule the build runs binds it")
                    .isFalse();
        }

        @Test
        void theModuleThatDecidesTheJavaIsTheOneNamed() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    properties("maven.compiler.release", "11")
                                            + "<modules><module>m</module></modules>"))
                    .pom("m/pom.xml", project("m", requireJava("(,8],[21,)")));

            assertThat(only("java.version").file())
                    .as("a person looks where the limit stands")
                    .isEqualTo("m/pom.xml");
            assertThat(only("package.openjdk-21-jdk-headless").file()).isEqualTo("m/pom.xml");
        }

        @Test
        void aPomThatWentUnreadIsNamedBesideAKnownLevel() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    properties("maven.compiler.release", "17")
                                            + "<modules><module>m</module></modules>"))
                    .pom("m/pom.xml", "<project><broken");

            assertThat(only("java.unread").note())
                    .as("it may set a limit the JDK chosen does not meet")
                    .contains("went unread");
        }

        @Test
        void aRuleOnlyManagedBindsAPluginTheBuildRuns() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                            <build>
                              <pluginManagement><plugins><plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-enforcer-plugin</artifactId>
                                <configuration><rules><requireJavaVersion>
                                  <version>21</version>
                                </requireJavaVersion></rules></configuration>
                              </plugin></plugins></pluginManagement>
                              <plugins><plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-enforcer-plugin</artifactId>
                              </plugin></plugins>
                            </build>
                            """));

            assertThat(only("java.version").value())
                    .as("the model read holds pluginManagement apart; the build runs this rule")
                    .isEqualTo("21");
        }

        @Test
        void aRuleNothingFillsInBesideAKnownLevelIsStillNamed() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("maven.compiler.release", "17")
                                    + requireJava("${env.JDK}")));

            assertThat(only("java.version").value()).as("what is known stays").isEqualTo("17");
            assertThat(only("java.unread").note())
                    .as("the JDK chosen may still be too low")
                    .contains("${env.JDK}");
        }

        @Test
        void anUpperLimitInOneModuleBindsTheReleaseOfAnother() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    "<modules><module>m</module></modules>"
                                            + properties("maven.compiler.release", "21")))
                    .pom("m/pom.xml", project("m", requireJava("[17,21)")));

            assertThat(only("package.openjdk-21-jdk-headless").unknown())
                    .as("the image holds one JDK, and it cannot meet both")
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"(,17]", "[,]", "(,)"})
        void anOpenStartTakesTheLowestPackagedInTheRange(String range) throws IOException {
            f.pom("pom.xml", project("child", requireJava(range)));

            assertThat(only("package.openjdk-8-jdk-headless").unknown())
                    .as("nothing asks for more, and it is packaged")
                    .isFalse();
        }

        @ParameterizedTest
        @CsvSource({
            "99999999999999999999",
            "'[99999999999999999999,)'",
            "not a version",
            "'[,'",
            "'[not-a-version]'",
            "'[foo,17]'",
            "'[17,foo]'",
            "'[21,17]'",
            "'[17,17)'"
        })
        void aVersionThatNamesNoJdkIsUnknownAndNeverGuessed(String written) throws IOException {
            f.pom("pom.xml", project("child", requireJava(written)));

            assertThat(only("java.version").unknown())
                    .as("neither a crash nor a default JDK that reads as asked for")
                    .isTrue();
            assertThat(detect().findings())
                    .as("a JDK named on a value that names none says it only stands in")
                    .filteredOn(x -> x.key().contains("-jdk"))
                    .isNotEmpty()
                    .allMatch(x -> x.note().startsWith("stands in"));
        }

        private static String requireJava(String range) {
            return plugin(
                    "org.apache.maven.plugins",
                    "maven-enforcer-plugin",
                    "<configuration><rules><requireJavaVersion><version>"
                            + range
                            + "</version></requireJavaVersion></rules></configuration>");
        }

        @Test
        void aReleaseAboveEveryPackagedJdkIsUnknownAndNeverGuessed() throws IOException {
            f.pom("pom.xml", project("child", properties("maven.compiler.release", "26")));

            assertThat(only("package.openjdk-26-jdk-headless").unknown())
                    .as("an older JDK cannot compile it")
                    .isTrue();
        }

        @Test
        void aPomAskingNoReleaseGetsTheDefaultJdk() throws IOException {
            f.pom("pom.xml", project("child", ""));

            assertThat(only("package.default-jdk-headless").value())
                    .as("Maven runs on a JDK even when nothing names one")
                    .isEqualTo("default-jdk-headless");
        }

        @Test
        void aReleaseFromAParentInTheLocalRepositoryIsTheMachines() throws IOException {
            f.localPom(
                            "org.example",
                            "above",
                            "1",
                            project("above", properties("maven.compiler.release", "25")))
                    .pom("pom.xml", project("child", Fixture.parent("above", "")));

            assertThat(only("java.version").origin())
                    .as("another machine may hold another parent, or none")
                    .isEqualTo(Finding.Origin.MACHINE);
            assertThat(only("package.openjdk-25-jdk-headless").origin())
                    .as("the package stands where what asks for it stands")
                    .isEqualTo(Finding.Origin.MACHINE);
        }

        @Test
        void aParentNotFoundMayAskARelease() throws IOException {
            f.pom("pom.xml", project("child", Fixture.parent("absent", "")));

            assertThat(only("java.version").unknown())
                    .as("the parent may name one, so nothing says none is asked")
                    .isTrue();
        }

        /** Harm prevented: the image holds a Maven with no JDK, which cannot start at all. */
        @Test
        void aParentNotFoundStillGetsTheHighestPackagedJdkAtItsLine() throws IOException {
            f.pom("pom.xml", project("child", Fixture.parent("absent", "")));
            Finding parent = only("unknown.org.example:absent:1");

            Finding jdk = standIn();

            assertThat(jdk.unknown()).as("installed, so Maven runs").isFalse();
            assertThat(List.of(jdk.file(), jdk.line()))
                    .as("beside the parent that would say which")
                    .isEqualTo(List.of(parent.file(), parent.line()));
            assertThat(List.of(only("java.version").file(), only("java.version").line()))
                    .as("the unknown release stands there too")
                    .isEqualTo(List.of(parent.file(), parent.line()));
            assertThat(jdk.note())
                    .as("says what it stands in for")
                    .contains("org.example:absent:1");
        }

        @Test
        void aModuleThatCannotBeReadStillGetsAJdk() throws IOException {
            f.pom("pom.xml", project("child", "<modules><module>m</module></modules>"))
                    .pom("m/pom.xml", "<project><broken");

            assertThat(only("java.version").unknown())
                    .as("the module may name one, so nothing says none is asked")
                    .isTrue();
            assertThat(standIn().unknown()).as("Maven still needs a JDK").isFalse();
        }

        @Test
        void aMissingModuleStillGetsAJdkAtItsLine() throws IOException {
            f.pom("pom.xml", project("child", "<modules><module>gone</module></modules>"));
            Finding module =
                    detect().findings().stream()
                            .filter(x -> x.unknown() && x.key().contains("module gone"))
                            .findFirst()
                            .orElseThrow();

            Finding jdk = standIn();

            assertThat(List.of(jdk.file(), jdk.line()))
                    .as("beside the module that would say which")
                    .isEqualTo(List.of(module.file(), module.line()));
        }

        /**
         * Harm prevented: the stand-in points at a pom that was read, not at the one that was not.
         */
        @Test
        void aParentTheGuardRefusesIsWhereTheStandInStands() throws IOException {
            String large = "<!-- " + "x".repeat(PomGuard.MAX_POM_BYTES) + " -->";
            f.localPom("org.example", "large", "1", large + project("large", ""))
                    .pom("pom.xml", project("child", Fixture.parent("large", "")));

            assertThat(standIn().file()).as("the refused parent").endsWith("large-1.pom");
        }

        @Test
        void aReleaseNothingFillsInStillGetsAJdk() throws IOException {
            f.pom("pom.xml", project("child", properties("maven.compiler.release", "${unset}")));

            assertThat(only("java.version").unknown()).as("the release stays unknown").isTrue();
            assertThat(standIn().unknown()).as("Maven still needs a JDK").isFalse();
        }

        // The highest packaged JDK compiles every release a lower one does.
        private Finding standIn() {
            List<Finding> jdks =
                    detect().findings().stream()
                            .filter(x -> x.key().matches("package\\.openjdk-\\d+-jdk-headless"))
                            .toList();
            assertThat(jdks).as("one JDK in %s", jdks).hasSize(1);
            int major = Integer.parseInt(jdks.get(0).value().replaceAll("\\D", ""));
            assertThat(major)
                    .as("the highest packaged, at least the one a release of 25 gets")
                    .isGreaterThanOrEqualTo(25);
            return jdks.get(0);
        }

        @Test
        void aWrapperThatRunsNeedsNoMavenPackage() throws IOException {
            f.pom("pom.xml", project("child", "")).pom(WRAPPER, ZIP).pom("mvnw", "");

            assertThat(detect().findings())
                    .as("the wrapper fetches its own, and a packaged one is another version")
                    .noneMatch(x -> x.key().equals("package.maven"));
        }

        @Test
        void wrapperPropertiesWithoutItsScriptStillNeedAMavenPackage() throws IOException {
            f.pom("pom.xml", project("child", "")).pom(WRAPPER, ZIP);

            assertThat(only("package.maven").file())
                    .as("nothing runs the wrapper, so mvn does")
                    .isEqualTo(WRAPPER);
        }

        @Test
        void noWrapperNeedsAMavenPackageAndSaysWhatThePomAsks() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    "maven-enforcer-plugin",
                                    """
                                    <executions><execution><configuration><rules><requireMavenVersion>
                                      <version>[3.9,)</version>
                                    </requireMavenVersion></rules></configuration></execution></executions>
                                    """)));

            Finding maven = only("package.maven");

            assertThat(maven.value()).isEqualTo("maven");
            assertThat(maven.line())
                    .as("where the version is asked")
                    .isEqualTo(only("maven.version").line());
            assertThat(maven.note()).as("a packaged one may not meet it").contains("[3.9,)");
        }

        @Test
        void aScriptOnlyWrapperCheckingAZipNeedsUnzip() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .pom(WRAPPER, "# pinned\n" + ONLY_SCRIPT + ZIP + DIGEST)
                    .pom("mvnw", "");

            assertThat(only("package.unzip").file()).isEqualTo(WRAPPER);
            assertThat(only("package.unzip").line()).as("the URL's line").isEqualTo(3);
        }

        @Test
        void aScriptOnlyWrapperWrittenWithSpacesNeedsUnzipToo() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .pom(
                            WRAPPER,
                            "distributionType = only-script\n"
                                    + ZIP
                                    + "distributionSha256Sum : abc\n")
                    .pom("mvnw", "");

            assertThat(only("package.unzip").line())
                    .as("the wrapper checks the zip's digest whatever the separator")
                    .isEqualTo(2);
        }

        @ParameterizedTest
        @CsvSource({"no digest,true,false", "no script type,false,true"})
        void aWrapperThatCanDoWithoutUnzipNamesNone(String why, boolean onlyScript, boolean digest)
                throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .pom(WRAPPER, (onlyScript ? ONLY_SCRIPT : "") + ZIP + (digest ? DIGEST : ""))
                    .pom("mvnw", "");

            assertThat(detect().findings()).as(why).noneMatch(x -> x.key().equals("package.unzip"));
        }

        @Test
        void aWrapperFetchingATarballNeedsNoUnzip() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .pom(
                            WRAPPER,
                            ONLY_SCRIPT
                                    + "distributionUrl=https\\://dl.example.org/apache-maven-3.9.15-bin.tar.gz\n"
                                    + DIGEST)
                    .pom("mvnw", "");

            assertThat(detect().findings())
                    .as("tar is in the image already")
                    .noneMatch(x -> x.key().equals("package.unzip"));
        }

        @Test
        void aReactorInstallsOneJdkTheHighestItAsks() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    "<modules><module>m</module></modules>"
                                            + properties("maven.compiler.release", "17")))
                    .pom("m/pom.xml", project("m", properties("maven.compiler.release", "25")));

            assertThat(detect().findings())
                    .extracting(Finding::key)
                    .as("two would be a choice the image does not make")
                    .filteredOn(key -> key.contains("-jdk"))
                    .containsExactly("package.openjdk-25-jdk-headless");
        }
    }

    /** Harm prevented: a person starts a task for a build that cannot run in one, unwarned. */
    @Nested
    class AContainerStartingBuild {
        private static final String TESTCONTAINERS =
                """
                <dependencies><dependency>
                  <groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId>
                  <version>1.20.0</version><scope>test</scope>
                </dependency></dependencies>
                """;

        @Test
        void testcontainersIsWarnedOfWithWhatStillWorks() throws IOException {
            f.pom("pom.xml", project("child", TESTCONTAINERS));

            assertThat(detect().unplaced())
                    .as("the person reads it before starting, with where it stands")
                    .singleElement()
                    .satisfies(
                            u -> {
                                assertThat(u.file()).as("where it stands").isEqualTo("pom.xml");
                                assertThat(u.line()).as("its line, not the pom's").isEqualTo(7);
                                assertThat(u.note())
                                        .as("what fails, and what still works")
                                        .contains("a task does not support")
                                        .contains("-DskipTests")
                                        .contains("skip themselves")
                                        .contains("nothing in porelo.yml changes that")
                                        .contains("any security class")
                                        .doesNotContain("-DskipITs");
                            });
        }

        @ParameterizedTest
        @CsvSource({
            "io.fabric8, docker-maven-plugin",
            "com.spotify, dockerfile-maven-plugin",
            "org.eclipse.jkube, kubernetes-maven-plugin",
            "org.eclipse.jkube, openshift-maven-plugin",
            "io.fabric8, fabric8-maven-plugin"
        })
        void aPluginThatStartsContainersIsWarnedOf(String group, String artifact)
                throws IOException {
            f.pom("pom.xml", project("child", plugin(group, artifact, "")));

            assertThat(detect().unplaced())
                    .as("its goals fail in a task")
                    .singleElement()
                    .satisfies(
                            u -> {
                                assertThat(u.note())
                                        .as("it names what fails")
                                        .contains(artifact)
                                        .contains("nothing in porelo.yml changes that")
                                        .contains("any security class")
                                        .contains("leaves those goals out")
                                        .contains("a task does not support");
                                assertThat(u.file()).as("where it stands").isEqualTo("pom.xml");
                                assertThat(u.line()).as("its line, not the pom's").isEqualTo(7);
                            });
        }

        @ParameterizedTest
        @CsvSource({"io.fabric8, docker-maven-plugin", "com.spotify, dockerfile-maven-plugin"})
        void noContainerClientIsInstalledWithNoDaemonToReach(String group, String artifact)
                throws IOException {
            f.pom("pom.xml", project("child", plugin(group, artifact, "")));

            assertThat(detect().findings())
                    .as("a client alone would look as if the build could run")
                    .noneMatch(x -> x.key().equals("package.docker"));
        }

        @Test
        void jibIsNotWarnedOfAsItNeedsNoDaemon() throws IOException {
            f.pom(
                    "pom.xml",
                    project("child", plugin("com.google.cloud.tools", "jib-maven-plugin", "")));

            assertThat(detect().unplaced()).as("it builds an image without one").isEmpty();
        }

        @Test
        void aPluginOnlyManagedIsNotWarnedOf() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                            <build><pluginManagement><plugins><plugin>
                              <groupId>io.fabric8</groupId><artifactId>docker-maven-plugin</artifactId>
                            </plugin></plugins></pluginManagement></build>
                            """));

            assertThat(detect().unplaced()).as("no build runs it").isEmpty();
        }

        @Test
        void aTestcontainersDependencyOnlyManagedIsNotWarnedOf() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "<dependencyManagement>" + TESTCONTAINERS + "</dependencyManagement>"));

            assertThat(detect().unplaced()).as("nothing uses it").isEmpty();
        }

        @Test
        void aPomWarnsOnceHoweverManyArtifactsItHolds() throws IOException {
            String dependencies =
                    """
                    <dependencies>
                      <dependency><groupId>org.testcontainers</groupId><artifactId>a</artifactId>
                        <version>1</version><scope>test</scope></dependency>
                      <dependency><groupId>org.testcontainers</groupId><artifactId>b</artifactId>
                        <version>1</version><scope>test</scope></dependency>
                    </dependencies>
                    """;
            f.pom("pom.xml", project("child", "<modules><module>m</module></modules>"))
                    .pom("m/pom.xml", project("m", dependencies));

            assertThat(detect().unplaced())
                    .as("one line per pom, not one per artifact")
                    .singleElement()
                    .satisfies(u -> assertThat(u.file()).as("the pom").isEqualTo("m/pom.xml"));
        }

        @Test
        void aModuleWithItsOwnIsNamedBesideWhatItInherits() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    "<modules><module>m</module></modules>" + TESTCONTAINERS))
                    .pom(
                            "m/pom.xml",
                            """
                            <project>
                              <modelVersion>4.0.0</modelVersion>
                              <parent>
                                <groupId>org.example</groupId><artifactId>child</artifactId>
                                <version>1</version>
                              </parent>
                              <artifactId>m</artifactId>
                              <dependencies><dependency>
                                <groupId>org.testcontainers</groupId><artifactId>kafka</artifactId>
                                <version>1</version><scope>test</scope>
                              </dependency></dependencies>
                            </project>
                            """);

            assertThat(detect().unplaced())
                    .as("the inherited one comes first and must not hide the module's own")
                    .extracting(u -> u.file())
                    .containsExactlyInAnyOrder("pom.xml", "m/pom.xml");
        }

        @Test
        void aBomStartsNothing() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                            <dependencies><dependency>
                              <groupId>org.testcontainers</groupId>
                              <artifactId>testcontainers-bom</artifactId>
                              <version>1</version><type>pom</type><scope>test</scope>
                            </dependency></dependencies>
                            """));

            assertThat(detect().unplaced()).as("it only names versions").isEmpty();
        }
    }

    /**
     * Harm prevented: a draft reads as complete while the build stops at its first phase, on a
     * refused host or a missing toolchain, for a JDK that a plugin picks or fetches of its own.
     */
    @Nested
    class AToolchainPlugin {
        private static final String FETCHING = "org.mvnsearch";
        private static final String FETCHING_ARTIFACT = "toolchains-maven-plugin";
        private static final String MAVENS = "maven-toolchains-plugin";

        private static String toolchain(String jdk) {
            return """
                    <executions><execution>
                      <goals><goal>toolchain</goal></goals>
                      <configuration><toolchains>%s</toolchains></configuration>
                    </execution></executions>
                    """
                    .formatted(jdk);
        }

        @Test
        void aPluginThatFetchesItsJdkIsWarnedOfWithTheJdkAndTheHost() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    FETCHING,
                                    FETCHING_ARTIFACT,
                                    toolchain(
                                            "<jdk><version>27</version><vendor>temurin</vendor></jdk>"
                                                    + "<testJdk><version>25</version></testJdk>"))));

            assertThat(detect().unplaced())
                    .as("the person reads it before the build stops on a refused host")
                    .singleElement()
                    .satisfies(
                            u -> {
                                assertThat(u.file()).as("where it stands").isEqualTo("pom.xml");
                                assertThat(u.line()).as("the plugin's line").isEqualTo(7);
                                assertThat(u.note())
                                        .as("the JDK it asks, for the build and its tests")
                                        .contains(FETCHING_ARTIFACT)
                                        .contains("JDK 27 (temurin)")
                                        .contains("JDK 25 for its tests")
                                        .as("where it fetches it from, and that none is opened")
                                        .contains("api.foojay.io")
                                        .contains("in porelo.yml under egress")
                                        .contains("no host is opened for it")
                                        .contains("toolchains.xml");
                            });
        }

        @Test
        void mavensToolchainPluginIsWarnedOfWithTheJdkItsManagementAsks() throws IOException {
            String managed =
                    """
                    <build>
                      <pluginManagement><plugins><plugin>
                        <artifactId>maven-toolchains-plugin</artifactId>
                        %s
                      </plugin></plugins></pluginManagement>
                      <plugins><plugin>
                        <artifactId>maven-toolchains-plugin</artifactId>
                      </plugin></plugins>
                    </build>
                    """
                            .formatted(toolchain("<jdk><version>27</version></jdk>"));
            f.pom("pom.xml", project("child", managed));

            assertThat(detect().unplaced())
                    .as("the build stops where no toolchains.xml holds that JDK")
                    .singleElement()
                    .satisfies(
                            u -> {
                                assertThat(u.line())
                                        .as(
                                                "the line of the plugin the build runs, not its management")
                                        .isEqualTo(16);
                                assertThat(u.note())
                                        .contains(MAVENS)
                                        .contains("JDK 27")
                                        .contains("toolchains.xml")
                                        .contains("a task does not have")
                                        .doesNotContain("api.foojay.io");
                            });
        }

        @Test
        void aToolchainPluginOnlyManagedIsNotWarnedOf() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                            <build><pluginManagement><plugins><plugin>
                              <artifactId>maven-toolchains-plugin</artifactId>
                              %s
                            </plugin></plugins></pluginManagement></build>
                            """
                                    .formatted(toolchain("<jdk><version>27</version></jdk>"))));

            assertThat(detect().unplaced()).as("no build runs it").isEmpty();
        }

        @Test
        void aToolchainPluginWithNoToolchainGoalIsNotWarnedOf() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    FETCHING,
                                    FETCHING_ARTIFACT,
                                    "<configuration><toolchains><jdk><version>27</version></jdk>"
                                            + "</toolchains></configuration>")));

            assertThat(detect().unplaced()).as("no goal of it runs").isEmpty();
        }

        @Test
        void aJdkWhoseVersionIsNotReadIsNamedAsSuch() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    MAVENS,
                                    toolchain("<jdk><version>${jdk.unset}</version></jdk>"))));

            assertThat(detect().unplaced())
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .as("a JDK the reader cannot read is not guessed")
                                            .contains("a JDK whose version is not read here")
                                            .contains("${jdk.unset}"));
        }

        @Test
        void aJdkTwoExecutionsAskIsNamedOnceAndAnotherGoalsIsNot() throws IOException {
            String executions =
                    """
                    <executions>
                      <execution><id>a</id><goals><goal>toolchain</goal></goals>
                        <configuration><toolchains><jdk><version>27</version></jdk></toolchains></configuration>
                      </execution>
                      <execution><id>b</id><goals><goal>toolchain</goal></goals>
                        <configuration><toolchains><jdk><version>27</version></jdk></toolchains></configuration>
                      </execution>
                      <execution><id>c</id><goals><goal>select-jdk-toolchain</goal></goals>
                        <configuration><toolchains><jdk><version>11</version></jdk></toolchains></configuration>
                      </execution>
                    </executions>
                    """;
            f.pom(
                    "pom.xml",
                    project("child", plugin("org.apache.maven.plugins", MAVENS, executions)));

            assertThat(detect().unplaced())
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .as("the JDK the toolchain goal asks, once")
                                            .containsOnlyOnce("JDK 27")
                                            .as("not what another goal is configured with")
                                            .doesNotContain("11"));
        }

        @Test
        void aTestJdkStatedApartAddsNoJdkNoneStates() throws IOException {
            String executions =
                    """
                    <executions>
                      <execution><id>a</id><goals><goal>toolchain</goal></goals>
                        <configuration><toolchains><jdk><version>27</version></jdk></toolchains></configuration>
                      </execution>
                      <execution><id>b</id><goals><goal>toolchain</goal></goals>
                        <configuration><toolchains><testJdk><version>25</version></testJdk></toolchains></configuration>
                      </execution>
                    </executions>
                    """;
            f.pom("pom.xml", project("child", plugin(FETCHING, FETCHING_ARTIFACT, executions)));

            assertThat(detect().unplaced())
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .contains("JDK 27")
                                            .contains("JDK 25 for its tests")
                                            .doesNotContain("none stated"));
        }

        @Test
        void aVendorFromAPropertyIsNamedAndOneNotReadIsNotGuessed() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("jdk.vendor", "zulu")
                                    + plugin(
                                            FETCHING,
                                            FETCHING_ARTIFACT,
                                            toolchain(
                                                    "<jdk><version>27</version><vendor>${jdk.vendor}</vendor></jdk>"
                                                            + "<testJdk><version>25</version><vendor>${jdk.unset}</vendor></testJdk>"))));

            assertThat(detect().unplaced())
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .as("a property the pom sets is read")
                                            .contains("JDK 27 (zulu)")
                                            .as("one it does not set is named as not read")
                                            .contains(
                                                    "JDK 25 (a vendor not read here, ${jdk.unset}) for its tests"));
        }

        @Test
        void mavensToolchainPluginLeavesOpenThatAnotherPluginWritesTheToolchain()
                throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    MAVENS,
                                    toolchain("<jdk><version>27</version></jdk>"))));

            assertThat(detect().unplaced())
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .as("a fetching plugin beside it can write the match")
                                            .contains("unless another plugin writes one")
                                            .doesNotContain("nothing in porelo.yml changes that"));
        }

        @Test
        void eachModuleThatRunsItIsWarnedOfOnce() throws IOException {
            String uses =
                    plugin(
                            "org.apache.maven.plugins",
                            MAVENS,
                            toolchain("<jdk><version>27</version></jdk>"));
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    "<modules><module>a</module><module>b</module></modules>"))
                    .pom("a/pom.xml", project("a", uses))
                    .pom("b/pom.xml", project("b", uses));

            assertThat(detect().unplaced())
                    .as("one warning per pom that runs it")
                    .extracting(u -> u.file())
                    .containsExactlyInAnyOrder("a/pom.xml", "b/pom.xml");
        }
    }

    /** Harm prevented: a build runs out of memory in a box sized without asking it. */
    @Nested
    class Memory {
        @Test
        void theJvmConfigsHeapIsNamed() throws IOException {
            f.pom("pom.xml", project("child", "")).pom(".mvn/jvm.config", "-Dx=1\n-Xmx4g\n");

            Finding memory = only("memory.maven");

            assertThat(memory.value()).isEqualTo("4g");
            assertThat(memory.part()).isEqualTo(Finding.Part.RULES);
            assertThat(memory.line()).isEqualTo(2);
        }

        @Test
        void theLargestHeapAnyModulesTestsAskForIsNamed() throws IOException {
            String runner =
                    plugin(
                            "org.apache.maven.plugins",
                            "maven-surefire-plugin",
                            "<configuration><argLine>-ea -Xmx512m</argLine></configuration>");
            f.pom("pom.xml", project("root", runner + "<modules><module>a</module></modules>"))
                    .pom("a/pom.xml", project("a", properties("argLine", "-Xmx2g")));

            assertThat(only("memory.tests").value()).as("2g is above 512m").isEqualTo("2g");
        }
    }

    /** Harm prevented: a task is refused a host its build reaches, or a credential is copied. */
    @Nested
    class Rules {
        @Test
        void aHostAndAKnownCredentialHaveKeysOfTheirOwn() throws IOException {
            f.pom("pom.xml", project("child", repository("corp", "https://nexus.example.org/m")))
                    .settings(
                            """
                            <settings><servers><server>
                              <id>corp</id><password>hunter2</password>
                            </server></servers></settings>
                            """);

            Finding host = only("host.nexus.example.org");
            Finding credential = only("credential.corp");

            assertThat(host.value()).isEqualTo("nexus.example.org");
            assertThat(host.origin()).isEqualTo(Finding.Origin.CHECKOUT);
            assertThat(credential.value()).as("by its id").isEqualTo("corp");
            assertThat(credential.origin()).isEqualTo(Finding.Origin.YOURS);
            assertThat(credential.file()).isEqualTo(".m2/settings.xml");
            assertThat(detect().asMap().toString())
                    .as("its value nowhere")
                    .doesNotContain("hunter2");
        }

        /**
         * Harm prevented: the person stores a credential the broker cannot send, or a secret's
         * value reaches the answer.
         */
        @ParameterizedTest
        @CsvSource(
                delimiter = '|',
                value = {
                    "<username>u</username><password>s3cr3t-plain</password>|basic|Base64",
                    "<username>u</username><password>${env.REPO_TOKEN}</password>|basic|REPO_TOKEN",
                    "<username>u</username><password>{s3cr3tMaven3=}</password>|basic|does not"
                            + " decrypt",
                    "<username>u</username><password>{[name=master,version=4.0]s3cr3tMaven4=}"
                            + "</password>|basic|does not decrypt",
                    "<configuration><httpHeaders><property><name>Private-Token</name>"
                            + "<value>s3cr3t-header</value></property></httpHeaders>"
                            + "</configuration>|header:Private-Token|token",
                    "<configuration><httpHeaders><property><name>Private-Token</name>"
                            + "<value>{s3cr3tMaven3=}</value></property></httpHeaders>"
                            + "</configuration>|header:Private-Token|does not decrypt",
                    "<privateKey>/keys/s3cr3t-key</privateKey><passphrase>s3cr3t-pass</passphrase>"
                            + "|unsupported|private key",
                    "<configuration><httpHeaders><property><name/>"
                            + "<value>s3cr3t-header</value></property></httpHeaders>"
                            + "</configuration>|unsupported|no name",
                    "<username>u</username><password>s3cr3t-plain</password>"
                            + "<configuration><httpHeaders><property><name>X-Custom</name>"
                            + "<value>s3cr3t-header</value></property></httpHeaders>"
                            + "</configuration>|unsupported|both"
                })
        void aCredentialSaysItsKindAndWhatToStoreButNeverItsValue(
                String server, String kind, String toStore) throws IOException {
            f.pom("pom.xml", project("child", repository("corp", "https://nexus.example.org/m")))
                    .settings(
                            "<settings><servers><server><id>corp</id>"
                                    + server
                                    + "</server></servers></settings>");

            Finding stated = only("credential-kind.corp");

            assertThat(stated.value()).as("what the broker reads").isEqualTo(kind);
            assertThat(stated.note()).as("what the person stores").contains(toStore);
            assertThat(List.of(stated.file(), stated.line()))
                    .as("at the server it stands for")
                    .isEqualTo(
                            List.of(
                                    only("credential.corp").file(),
                                    only("credential.corp").line()));
            assertThat(detect().asMap().toString())
                    .as("no secret's value, encrypted or not")
                    .doesNotContain("s3cr3t");
        }

        /**
         * Harm prevented: a task's settings send a repository the person's mirror takes past that
         * mirror, where the egress refuses it.
         */
        @Test
        void aMirrorIsARepositoryWithWhatItTakesAsTheExtension() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .settings(
                            """
                            <settings><mirrors><mirror>
                              <id>inhouse</id>
                              <url>https://repo.example.org/maven</url>
                              <mirrorOf>external:*,!local</mirrorOf>
                            </mirror></mirrors></settings>
                            """);

            Finding repository = only("repository.inhouse");
            Finding takes = only("extension.mirrorOf.inhouse");

            assertThat(repository.value())
                    .as("its URL")
                    .isEqualTo("https://repo.example.org/maven");
            assertThat(takes.value()).as("as written").isEqualTo("external:*,!local");
            assertThat(List.of(takes.file(), takes.line()))
                    .as("at the mirror")
                    .isEqualTo(List.of(repository.file(), repository.line()));
        }

        @Test
        void aRepositoryOfAPomIsARepositoryTakingOnlyItself() throws IOException {
            f.pom("pom.xml", project("child", repository("corp", "https://nexus.example.org/m")));

            assertThat(only("repository.corp").value()).isEqualTo("https://nexus.example.org/m");
            assertThat(detect().findings())
                    .as("it stands for itself alone")
                    .noneMatch(x -> x.key().startsWith("extension."));
        }

        /** Harm prevented: a proxy of the same id hides the mirror, and the task goes around it. */
        @Test
        void aProxyUnderTheMirrorsIdDoesNotHideTheMirror() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .settings(
                            """
                            <settings>
                              <proxies><proxy><id>inhouse</id><host>proxy.example.org</host>
                                <port>3128</port></proxy></proxies>
                              <mirrors><mirror><id>inhouse</id><mirrorOf>*</mirrorOf>
                                <url>https://repo.example.org/maven</url></mirror></mirrors>
                            </settings>
                            """);

            assertThat(only("repository.inhouse").value())
                    .isEqualTo("https://repo.example.org/maven");
            assertThat(only("extension.mirrorOf.inhouse").value()).isEqualTo("*");
        }

        /** Harm prevented: one of two addresses under one name is dropped without a word. */
        @Test
        void twoAddressesUnderOneIdAreUnknownAndNeitherIsSent() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    "<modules><module>a</module><module>b</module></modules>"))
                    .pom("a/pom.xml", project("a", repository("corp", "https://one.example.org/m")))
                    .pom(
                            "b/pom.xml",
                            project("b", repository("corp", "https://two.example.org/m")));

            assertThat(detect().findings())
                    .noneMatch(x -> x.key().equals("repository.corp"))
                    .anyMatch(x -> x.unknown() && x.key().equals("unknown.repository.corp"));
        }

        /** Harm prevented: the task's Maven picks another of two mirrors than the person's does. */
        @Test
        void mirrorsAreAnsweredInTheOrderOfTheSettings() throws IOException {
            f.pom("pom.xml", project("child", repository("corp", "https://corp.example.org/m")))
                    .settings(
                            """
                            <settings><mirrors>
                              <mirror><id>first</id><mirrorOf>central,other</mirrorOf>
                                <url>https://first.example.org/m</url></mirror>
                              <mirror><id>second</id><mirrorOf>*</mirrorOf>
                                <url>https://second.example.org/m</url></mirror>
                            </mirrors></settings>
                            """);

            List<String> keys = detect().findings().stream().map(Finding::key).toList();

            assertThat(keys.indexOf("extension.mirrorOf.first"))
                    .as("Maven tries patterns in the order the settings write them")
                    .isNotNegative()
                    .isLessThan(keys.indexOf("extension.mirrorOf.second"));
        }

        /** Harm prevented: a mirror of some layouts only takes every repository in the task. */
        @Test
        void aMirrorOfSomeLayoutsOnlyIsUnknown() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    "<repositories><repository><id>p</id>"
                                            + "<url>https://p2.example.org/r</url><layout>p2</layout>"
                                            + "</repository></repositories>"))
                    .settings(
                            """
                            <settings><mirrors><mirror><id>m</id><mirrorOf>*</mirrorOf>
                              <mirrorOfLayouts>p2</mirrorOfLayouts>
                              <url>https://mirror.example.org/m</url></mirror></mirrors></settings>
                            """);

            assertThat(detect().findings())
                    .noneMatch(x -> x.key().equals("extension.mirrorOf.m"))
                    .anyMatch(x -> x.unknown() && x.key().equals("unknown.mirrorOf.m"));
        }

        /** Harm prevented: a name or pattern Porelo cannot carry is sent and split or refused. */
        @ParameterizedTest
        @CsvSource({"in.house, *", "inhouse, '*,a;b'", "inhouse, '*, b'"})
        void whatPoreloCannotCarryIsUnknownAndNeverSent(String id, String mirrorOf)
                throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .settings(
                            """
                            <settings><mirrors><mirror>
                              <id>%s</id><url>https://repo.example.org/m</url>
                              <mirrorOf>%s</mirrorOf>
                            </mirror></mirrors></settings>
                            """
                                    .formatted(id, mirrorOf));

            assertThat(detect().findings())
                    .as("nothing goes into the draft for it")
                    .noneMatch(x -> x.key().startsWith("extension."))
                    .noneMatch(x -> x.key().equals("repository.in.house"))
                    .anyMatch(x -> x.unknown() && x.key().contains(id));
        }

        @Test
        void aRepositoryWithoutAServerNamesNoCredential() throws IOException {
            f.pom("pom.xml", project("child", repository("open", "https://open.example.org/m")));

            assertThat(detect().findings())
                    .as("the person is not sent to store what does not exist")
                    .noneMatch(x -> x.key().startsWith("credential."));
        }

        @Test
        void builtInCentralStandsAsTheMachinesWithNoFile() throws IOException {
            f.pom("pom.xml", project("child", ""));

            Finding central =
                    detect().findings().stream()
                            .filter(x -> x.key().equals("host.repo.maven.apache.org"))
                            .findFirst()
                            .orElseThrow();

            assertThat(central.origin()).isEqualTo(Finding.Origin.MACHINE);
            assertThat(central.file()).as("the contract's way of saying no file").isEmpty();
        }

        /**
         * Harm prevented: a host the checkout chose passes for Maven's own Central, and the person
         * lets it in without reading it.
         */
        @Test
        void aCentralTheCheckoutMovedElsewhereIsNotCalledMavensBuiltIn() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "<properties><maven.repo.central>https://corp.example.org/m2"
                                    + "</maven.repo.central></properties>"));

            assertThat(detect().findings())
                    .as("the moved Central, as a repository and a plugin repository")
                    .filteredOn(x -> x.key().equals("host.corp.example.org"))
                    .hasSize(2)
                    .allSatisfy(
                            x ->
                                    assertThat(x.note())
                                            .as("not Maven's default")
                                            .doesNotContain("built-in"));
        }

        @Test
        void whatIsUnknownCrossesTheContractWithItsPlace() throws IOException {
            f.pom("pom.xml", project("child", repository("r", "https://${nope}/m")));

            Finding unknown = only("unknown.${nope}");

            assertThat(unknown.unknown()).isTrue();
            assertThat(unknown.value()).as("the contract refuses a value here").isEmpty();
            assertThat(unknown.file()).isEqualTo("pom.xml");
        }

        @Test
        void anExtensionIsAnUnplacedFindingNamingItsFile() throws IOException {
            f.pom("pom.xml", project("child", "")).pom(".mvn/extensions.xml", "<extensions/>");

            assertThat(detect().unplaced())
                    .as("its code is never loaded, so the answer says what it leaves out")
                    .anyMatch(u -> u.file().equals(".mvn/extensions.xml"));
        }

        /** Harm prevented: a person is told of a gap and not how to close it themselves. */
        @ParameterizedTest
        @CsvSource({
            ".mvn/extensions.xml, <extensions/>",
            ".mvn/maven.config, -f",
            ".mvn/jvm.config, -javaagent:agent.jar"
        })
        void whatTheAnswerLeavesOutSaysWhereToWriteItInTheFile(String file, String content)
                throws IOException {
            f.pom("pom.xml", project("child", "")).pom(file, content + "\n");

            assertThat(detect().unplaced())
                    .as("the keys a draft itself writes, each with an example")
                    .filteredOn(u -> u.file().equals(file))
                    .isNotEmpty()
                    .allSatisfy(
                            u ->
                                    assertThat(u.note())
                                            .as("the host's key, and the tool's")
                                            .contains("egress.domains")
                                            .contains("domains: [\"repo.example.org\"]")
                                            .contains("image.run")
                                            .contains("RUN apt-get update")
                                            .as("in the file Porelo writes")
                                            .contains("add it in porelo.yml")
                                            .doesNotContain("project.yml"));
        }

        /** Harm prevented: a build stops on a host its plugin names in plain sight. */
        @Test
        void aJavadocLinkIsAHostAtItsLine() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    "maven-javadoc-plugin",
                                    """
                                    <configuration><links>
                                      <link>https://docs.example.org/api/</link>
                                    </links></configuration>
                                    """)));

            Finding host = only("host.docs.example.org");

            assertThat(host.file()).as("where it stands").isEqualTo("pom.xml");
            assertThat(host.line()).as("the link's own line").isEqualTo(10);
            assertThat(host.note()).as("what asks it").contains("maven-javadoc-plugin");
        }

        @Test
        void aJavadocLinkInAnExecutionIsAHostToo() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    "maven-javadoc-plugin",
                                    """
                                    <executions><execution><id>a</id>
                                      <configuration><links>
                                        <link>https://docs.example.org/api/</link>
                                      </links></configuration>
                                    </execution></executions>
                                    """)));

            assertThat(only("host.docs.example.org").value())
                    .as("mapstruct's stands so")
                    .isEqualTo("docs.example.org");
        }

        @Test
        void aJavadocLinkManagedForAPluginTheBuildRunsIsAHost() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                            <build>
                              <pluginManagement><plugins><plugin>
                                <artifactId>maven-javadoc-plugin</artifactId>
                                <configuration><links>
                                  <link>https://docs.example.org/api/</link>
                                </links></configuration>
                              </plugin></plugins></pluginManagement>
                              <plugins><plugin>
                                <artifactId>maven-javadoc-plugin</artifactId>
                              </plugin></plugins>
                            </build>
                            """));

            assertThat(only("host.docs.example.org").value())
                    .as("the build runs it with what is managed")
                    .isEqualTo("docs.example.org");
        }

        @Test
        void aLinkInAProfileTheMachineDecidesOpensNothing() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                            <profiles><profile><id>win</id>
                              <activation><os><family>windows</family></os></activation>
                              <build><plugins><plugin>
                                <artifactId>maven-javadoc-plugin</artifactId>
                                <configuration><links>
                                  <link>https://docs.example.org/api/</link>
                                </links></configuration>
                              </plugin></plugins></build>
                            </profile></profiles>
                            """));

            assertThat(detect().findings())
                    .as("what such a profile holds is not answered, and opens no host")
                    .noneMatch(x -> x.key().equals("host.docs.example.org"));
        }

        @ParameterizedTest
        @CsvSource({"../other/target/site/apidocs", "/opt/javadoc/api", "file:///opt/javadoc/api"})
        void aLocalLinkNeedsNoHostAndIsNoUnknown(String link) throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    "maven-javadoc-plugin",
                                    "<configuration><links><link>"
                                            + link
                                            + "</link></links></configuration>")));

            assertThat(detect().findings())
                    .as("javadoc reads it from disk")
                    .noneMatch(x -> x.key().startsWith("unknown."));
        }

        @Test
        void aPluginsHostNamesNoCredential() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "child",
                                    plugin(
                                            "org.apache.maven.plugins",
                                            "maven-javadoc-plugin",
                                            "<configuration><links>"
                                                    + "<link>https://docs.example.org/</link>"
                                                    + "</links></configuration>")))
                    .settings(
                            """
                            <settings><servers><server>
                              <id>maven-javadoc-plugin</id><username>u</username>
                            </server></servers></settings>
                            """);

            assertThat(detect().findings())
                    .as("no plugin looks a server up by its own name")
                    .noneMatch(x -> x.key().startsWith("credential."));
            assertThat(only("host.docs.example.org").note())
                    .as("nor is one missing")
                    .doesNotContain("credential");
        }

        @Test
        void aJavadocPluginOnlyManagedOpensNothing() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                            <build><pluginManagement><plugins><plugin>
                              <artifactId>maven-javadoc-plugin</artifactId>
                              <configuration><links>
                                <link>https://docs.example.org/api/</link>
                              </links></configuration>
                            </plugin></plugins></pluginManagement></build>
                            """));

            assertThat(detect().findings())
                    .as("no build runs it")
                    .noneMatch(x -> x.key().equals("host.docs.example.org"));
        }

        @Test
        void aJavadocLinkThatNamesNoHostIsSaid() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.apache.maven.plugins",
                                    "maven-javadoc-plugin",
                                    "<configuration><links><link>${not.set}/api/</link></links>"
                                            + "</configuration>")));

            assertThat(detect().findings())
                    .as("unknown, rather than left out")
                    .anyMatch(
                            x ->
                                    x.unknown()
                                            && x.key().contains("not.set")
                                            && x.note().contains("maven-javadoc-plugin"));
        }

        /** Harm prevented: a build stops on the key servers its signature check asks by default. */
        @Test
        void pgpverifysDefaultKeyServersAreHostsAtThePlugin() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin("org.simplify4u.plugins", "pgpverify-maven-plugin", "")));

            for (String name :
                    new String[] {"keyserver.ubuntu.com", "keys.openpgp.org", "pgp.mit.edu"}) {
                Finding host = only("host." + name);
                assertThat(host.line()).as("the plugin's line").isEqualTo(7);
                assertThat(host.note()).as("what asks it").contains("pgpverify-maven-plugin");
            }
        }

        @Test
        void aBlankKeyServerLeavesTheDefaults() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.simplify4u.plugins",
                                    "pgpverify-maven-plugin",
                                    "<configuration><pgpKeyServer>  </pgpKeyServer></configuration>")));

            assertThat(only("host.keys.openpgp.org").value())
                    .as("a blank says nothing, so the plugin takes its own")
                    .isEqualTo("keys.openpgp.org");
        }

        @Test
        void pgpverifysOwnKeyServerReplacesTheDefaults() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            plugin(
                                    "org.simplify4u.plugins",
                                    "pgpverify-maven-plugin",
                                    "<configuration><pgpKeyServer>hkps://keys.example.org,"
                                            + " hkps://more.example.org</pgpKeyServer>"
                                            + "</configuration>")));

            assertThat(detect().findings())
                    .as("only what it says")
                    .anyMatch(x -> x.key().equals("host.keys.example.org"))
                    .anyMatch(x -> x.key().equals("host.more.example.org"))
                    .noneMatch(x -> x.key().equals("host.keys.openpgp.org"));
        }

        /** Harm prevented: a plugin's extension leaves the same gap with no way to close it. */
        @Test
        void aPomsExtensionSaysWhereToWriteItInTheFile() throws IOException {
            f.pom(
                    "pom.xml",
                    project("child", plugin("org.example", "p", "<extensions>true</extensions>")));

            assertThat(detect().unplaced())
                    .as("the same gap, from a plugin")
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .as("the host's key, and the tool's")
                                            .contains("egress.domains")
                                            .contains("image.run")
                                            .as("in the file Porelo writes")
                                            .contains("add it in porelo.yml"));
        }

        /** Harm prevented: a person trusts a path that does not exist in the task. */
        @Test
        void aLocalRepositoryNeedsNoHostAndItsPathInTheTask() throws IOException {
            f.pom("pom.xml", project("child", repository("r", "file:///srv/repo")));

            assertThat(detect().unplaced())
                    .as("no host is missing, but the path may be")
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .as("what is true of each")
                                            .contains("needs no host")
                                            .contains("must exist in the task")
                                            .doesNotContain("needs nothing"));
        }

        /** Harm prevented: a person edits the project file for what only the file's mode fixes. */
        @Test
        void aFileThatCannotBeReadPromisesNoKey() throws IOException {
            f.pom("pom.xml", project("child", "")).pom(".mvn/maven.config", "-X\n");
            java.nio.file.Path config = f.checkout.resolve(".mvn/maven.config");
            java.nio.file.Files.setPosixFilePermissions(config, java.util.Set.of());

            assertThat(detect().unplaced())
                    .as("the build stops on it too")
                    .filteredOn(u -> u.file().equals(".mvn/maven.config"))
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .as("nothing to write")
                                            .contains("nothing in porelo.yml changes that")
                                            .doesNotContain("egress.domains"));
        }

        /** Harm prevented: the wrapper's Maven and host go unsaid, and so does how to add them. */
        @Test
        void aWrapperFileTooLargeSaysWhereToWriteWhatItNeeds() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .pom(".mvn/wrapper/maven-wrapper.properties", "a=b\n".repeat(300_000));

            assertThat(detect().unplaced())
                    .as("what stands in it is not read")
                    .filteredOn(u -> u.file().equals(".mvn/wrapper/maven-wrapper.properties"))
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .contains("egress.domains")
                                            .as("in the file Porelo writes")
                                            .contains("add it in porelo.yml"));
        }

        /** Harm prevented: what a file too large to read asks is unknown, and unsaid how to add. */
        @Test
        void aFileTooLargeSaysWhereToWriteWhatItNeeds() throws IOException {
            f.pom("pom.xml", project("child", "")).pom(".mvn/maven.config", "-X\n".repeat(600_000));

            assertThat(detect().unplaced())
                    .as("what stands in it is not read")
                    .filteredOn(u -> u.file().equals(".mvn/maven.config"))
                    .singleElement()
                    .satisfies(
                            u ->
                                    assertThat(u.note())
                                            .as("the host's key")
                                            .contains("too large")
                                            .contains("egress.domains")
                                            .as("in the file Porelo writes")
                                            .contains("add it in porelo.yml"));
        }

        @Test
        void aWholeAnswerCrossesTheWireUnchanged() throws IOException {
            f.pom("pom.xml", project("child", repository("r", "https://r.example.org/")))
                    .pom(".mvn/extensions.xml", "<extensions/>");

            Detection answer = detect();

            assertThat(Detection.of(answer.asMap()))
                    .as("nothing the contract would refuse on the other side")
                    .isEqualTo(answer);
        }
    }

    /** Harm prevented: a build the real settings steer differently is answered without them. */
    @Nested
    class GlobalSettingsAndProxies {
        private static final String PROXY =
                """
                <settings><proxies><proxy>
                  <id>office</id><host>proxy.example.org</host><port>3128</port>
                  <username>me</username><password>hunter2</password>
                </proxy></proxies></settings>
                """;

        @Test
        void anActiveProxyIsAHostAndItsCredentialIsNamedOnly() throws IOException {
            f.pom("pom.xml", project("child", "")).settings(PROXY);

            Finding proxy = only("host.proxy.example.org");

            assertThat(proxy.note()).as("what it is").contains("proxy office");
            assertThat(proxy.origin()).isEqualTo(Finding.Origin.YOURS);
            assertThat(only("credential.office").value()).isEqualTo("office");
            assertThat(detect().asMap().toString())
                    .as("neither the name nor the password travels")
                    .doesNotContain("hunter2")
                    .doesNotContain("<username>")
                    .doesNotContain("\"me\"");
        }

        @Test
        void anInactiveProxyIsNoHost() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .settings(
                            PROXY.replace(
                                    "<id>office</id>", "<id>office</id><active>false</active>"));

            assertThat(detect().findings())
                    .as("Maven does not go through it")
                    .noneMatch(x -> x.key().equals("host.proxy.example.org"));
        }

        @Test
        void aProxyFromTheMachineIsUnknown() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .settings(PROXY.replace("proxy.example.org", "${env.PROXY_HOST}"));

            assertThat(only("unknown.${env.PROXY_HOST}").file()).isEqualTo(".m2/settings.xml");
        }

        @Test
        void globalSettingsNamedInMavenConfigAreTheCheckouts() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .pom(".mvn/maven.config", "-gs\nci/global.xml\n")
                    .pom(
                            "ci/global.xml",
                            """
                            <settings><mirrors><mirror>
                              <id>m</id><mirrorOf>*</mirrorOf><url>https://global.example.org/</url>
                            </mirror></mirrors></settings>
                            """);

            Finding mirror = only("host.global.example.org");

            assertThat(mirror.file()).isEqualTo("ci/global.xml");
            assertThat(mirror.origin()).isEqualTo(Finding.Origin.CHECKOUT);
        }

        @Test
        void globalSettingsThatAreMissingAreNamed() throws IOException {
            f.pom("pom.xml", project("child", "")).pom(".mvn/maven.config", "-gs\nmissing.xml\n");

            assertThat(only("unknown.missing.xml").note())
                    .as("Maven stops on them")
                    .contains("not there");
        }

        @Test
        void theWrappersOwnInstallationIsReadAsTheMachines() throws IOException {
            f.pom("pom.xml", project("child", ""))
                    .pom(
                            ".mvn/wrapper/maven-wrapper.properties",
                            "distributionUrl = https\\://x.example.org/apache-maven-3.9.15-bin.zip\n");
            Path conf =
                    f.home.resolve(
                            ".m2/wrapper/dists/apache-maven-3.9.15-bin/abc/apache-maven-3.9.15/conf");
            Files.createDirectories(conf);
            Files.writeString(
                    conf.resolve("settings.xml"),
                    """
                    <settings><mirrors><mirror>
                      <id>m</id><mirrorOf>*</mirrorOf><url>https://installed.example.org/</url>
                    </mirror></mirrors></settings>
                    """);

            Finding mirror = only("host.installed.example.org");

            assertThat(mirror.origin())
                    .as("in the home, but the person did not write it")
                    .isEqualTo(Finding.Origin.MACHINE);
            assertThat(mirror.file()).startsWith(".m2/wrapper/dists/");
        }
    }

    /** Harm prevented: a task's Maven cannot reach what the broker serves it. */
    @Nested
    class Configure {
        @Test
        void noRepositoryNeedsNoFile() {
            assertThat(new MavenEcosystem().configure(List.of()))
                    .as("the person's task keeps Maven's defaults")
                    .isEmpty();
        }

        @Test
        void aNameOrUrlCannotBreakTheFile() {
            File file =
                    new MavenEcosystem()
                            .configure(
                                    List.of(new Repository("a&b", "http://127.0.0.1:9420/a?x=<y>")))
                            .get(0);

            assertThat(file.path()).isEqualTo(".m2/settings.xml");
            assertThat(file.ownerOnly()).as("it holds no credential").isFalse();
            assertThat(file.content())
                    .as("written as text, not as markup")
                    .contains("<id>a&amp;b</id>")
                    .contains("?x=&lt;y&gt;");
        }

        private static final Repository INHOUSE =
                new Repository("inhouse", "http://127.0.0.1:9420/inhouse/");

        private static String settingsFor(Map<String, Object> extension) {
            return new MavenEcosystem()
                    .configure(List.of(INHOUSE), new Extension(extension))
                    .get(0)
                    .content();
        }

        /**
         * Harm prevented: a repository the person's mirror takes is sent past it, where the egress
         * refuses it.
         */
        @Test
        void aMirrorOfEveryRepositoryTakesEveryRepositoryToTheBroker() {
            String settings = settingsFor(Map.of("mirrorOf", Map.of("inhouse", "*")));

            assertThat(settings)
                    .as("Maven's own Central goes to the broker too")
                    .contains("<mirrorOf>*</mirrorOf>")
                    .doesNotContain("<mirrorOf>inhouse</mirrorOf>");
        }

        @Test
        void withoutTheExtensionARepositoryTakesOnlyItself() {
            assertThat(
                            new MavenEcosystem()
                                    .configure(List.of(INHOUSE), Extension.NONE)
                                    .get(0)
                                    .content())
                    .contains("<mirrorOf>inhouse</mirrorOf>");
        }

        /** Harm prevented: a block Porelo passes unread changes the task's settings unchecked. */
        @ParameterizedTest
        @ValueSource(strings = {"notAMap", "badPattern", "unknownName", "unknownKey"})
        void aBlockThatDoesNotFitIsRefusedNamingItsKeyButNotItsValue(String which) {
            Map<String, Object> block =
                    switch (which) {
                        case "notAMap" -> Map.of("mirrorOf", "s3cr3t");
                        case "badPattern" -> Map.of("mirrorOf", Map.of("inhouse", "s3cr3t;x"));
                        case "unknownName" -> Map.of("mirrorOf", Map.of("other", "s3cr3t"));
                        default -> Map.of("s3cr3tKey", "s3cr3t");
                    };

            assertThatThrownBy(() -> settingsFor(block))
                    .as("refused as unreadable")
                    .isInstanceOfSatisfying(
                            EcosystemRefused.class,
                            e -> {
                                assertThat(e.reason())
                                        .isEqualTo(EcosystemRefused.Reason.UNREADABLE);
                                assertThat(e.detail())
                                        .as("names the key")
                                        .contains("extensions.maven");
                                assertThat(e.detail().replace("s3cr3tKey", ""))
                                        .as("never a value")
                                        .doesNotContain("s3cr3t");
                            });
        }
    }

    /**
     * Harm prevented: a bumped Maven adds a type, and the executable fails on the first pom using
     * it.
     */
    @Test
    void everyModelTypeIsRegisteredForReflection() throws Exception {
        String metadata =
                new String(
                        getClass()
                                .getResourceAsStream(
                                        "/META-INF/native-image/org.fuin.porelo/porelo-ecosystem-maven/reachability-metadata.json")
                                .readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
        java.net.URL model =
                org.apache.maven.model.Model.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation();

        try (java.util.zip.ZipFile jar =
                new java.util.zip.ZipFile(Path.of(model.toURI()).toFile())) {
            List<String> types =
                    jar.stream()
                            .map(java.util.zip.ZipEntry::getName)
                            .filter(n -> n.matches("org/apache/maven/model/[A-Z][A-Za-z]*\\.class"))
                            .map(n -> n.replace('/', '.').replace(".class", ""))
                            .toList();

            assertThat(types).as("the jar's types were found").hasSizeGreaterThan(40);
            assertThat(types)
                    .as("a pom's ${project.…} walks them by reflection")
                    .allMatch(t -> metadata.contains("\"" + t + "\""), "registered");
        }
    }
}
