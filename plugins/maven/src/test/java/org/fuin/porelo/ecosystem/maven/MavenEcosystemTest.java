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
import org.fuin.porelo.ecosystem.api.Detection;
import org.fuin.porelo.ecosystem.api.EcosystemRefused;
import org.fuin.porelo.ecosystem.api.File;
import org.fuin.porelo.ecosystem.api.Finding;
import org.fuin.porelo.ecosystem.api.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    /** Harm prevented: a settings file nobody could read is taken for one naming nothing. */
    @Test
    void settingsThatCannotBeReadAreUnreadableAndNotEmpty() throws IOException {
        f.pom("pom.xml", project("child", "")).settings("<settings><broken>");

        assertThatThrownBy(this::detect)
                .as("the reason says what to look at")
                .isInstanceOfSatisfying(
                        EcosystemRefused.class,
                        e -> {
                            assertThat(e.reason()).isEqualTo(EcosystemRefused.Reason.UNREADABLE);
                            assertThat(e.detail()).contains("settings.xml");
                        });
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
            assertThat(only("package.zlib1g-dev").line()).as("the plugin's line").isPositive();
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
            assertThat(detect().findings())
                    .as("the default JDK would be a guess")
                    .noneMatch(x -> x.key().contains("-jdk"));
        }

        @Test
        void aModuleThatCannotBeReadMayAskARelease() throws IOException {
            f.pom("pom.xml", project("child", "<modules><module>m</module></modules>"))
                    .pom("m/pom.xml", "<project><broken");

            assertThat(only("java.version").unknown())
                    .as("the module may name one, so nothing says none is asked")
                    .isTrue();
            assertThat(detect().findings())
                    .as("the default JDK would be a guess")
                    .noneMatch(x -> x.key().contains("-jdk"));
        }

        @Test
        void aReleaseNothingFillsInNamesNoJdk() throws IOException {
            f.pom("pom.xml", project("child", properties("maven.compiler.release", "${unset}")));

            assertThat(detect().findings())
                    .as("the unknown stays, and no JDK is guessed beside it")
                    .noneMatch(x -> x.key().contains("jdk"));
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
                            "distributionUrl=https://x.example.org/apache-maven-3.9.15-bin.zip\n");
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
