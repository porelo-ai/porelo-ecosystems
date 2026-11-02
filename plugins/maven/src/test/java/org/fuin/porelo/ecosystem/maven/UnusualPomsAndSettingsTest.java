package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.fuin.porelo.ecosystem.maven.Fixture.parent;
import static org.fuin.porelo.ecosystem.maven.Fixture.project;
import static org.fuin.porelo.ecosystem.maven.Fixture.properties;
import static org.fuin.porelo.ecosystem.maven.Fixture.repository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UnusualPomsAndSettingsTest {
    @TempDir Path root;
    Fixture f;

    @BeforeEach
    void fixture() throws IOException {
        f = new Fixture(root);
    }

    private static List<String> hosts(RepositoryHostsAnswer answer) {
        return answer.hosts().stream().map(RepositoryHost::host).distinct().toList();
    }

    private static RepositoryHost host(RepositoryHostsAnswer answer, String name) {
        return answer.hosts().stream()
                .filter(h -> h.host().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " not in " + answer));
    }

    /** Settings with one active profile holding what is given. */
    private Fixture settingsProfile(String body) throws IOException {
        return f.settings(
                """
                <settings>
                  <profiles>
                    <profile>
                      <id>corp</id>
                %s
                    </profile>
                  </profiles>
                  <activeProfiles><activeProfile>corp</activeProfile></activeProfiles>
                </settings>
                """
                        .formatted(body));
    }

    /** Harm prevented: one broken pom takes the whole answer down. */
    @Nested
    class APomThatBreaksTheBuilder {
        @Test
        void anInvalidTimestampFormatIsNamedAndTheRestAnswers() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "root",
                                    repository("r", "https://r.example.org/m")
                                            + "  <modules><module>bad</module></modules>\n"))
                    .pom(
                            "bad/pom.xml",
                            project("bad", properties("maven.build.timestamp.format", "invalid")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer)).as("the rest answers").contains("r.example.org");
            assertThat(answer.unknowns())
                    .as("the pom named")
                    .anyMatch(u -> u.place().file().equals("bad/pom.xml"));
        }

        private static String doubling(String reference) {
            StringBuilder levels = new StringBuilder("<p0>xxxxxxxxxxxxxxxx</p0>");
            for (int i = 1; i <= 40; i++) {
                levels.append(
                        "<p%d>%s%s</p%d>"
                                .formatted(
                                        i,
                                        reference.formatted(i - 1),
                                        reference.formatted(i - 1),
                                        i));
            }
            return levels.toString();
        }

        @Timeout(120)
        @Test
        void manyReferencesToALargeValueAreNamed() throws IOException {
            String large = "x".repeat(1024 * 1024);
            String references = "${large}".repeat(100_000);
            f.pom(
                    "pom.xml",
                    project(
                            "bomb",
                            "  <properties><large>"
                                    + large
                                    + "</large><many>"
                                    + references
                                    + "</many></properties>\n"
                                    + repository("r", "https://${many}.example.org/")));

            assertThat(f.answer().unknowns())
                    .as("an answer, not a crash")
                    .anyMatch(u -> u.why().startsWith("refused"));
        }

        @Test
        void aSiblingOfARefusedModuleStillAnswers() throws IOException {
            f.pom(
                            "pom.xml",
                            project(
                                    "root",
                                    "  <modules><module>bomb</module><module>clean</module></modules>\n"))
                    .pom(
                            "bomb/pom.xml",
                            project(
                                    "bomb",
                                    "  <properties>"
                                            + doubling("${p%d}")
                                            + "</properties>\n"
                                            + repository("b", "https://${p40}.example.org/")))
                    .pom(
                            "clean/pom.xml",
                            project("clean", repository("c", "https://clean.example.org/m")));

            assertThat(hosts(f.answer()))
                    .as("what one module asked for does not count against the next")
                    .contains("clean.example.org");
        }

        @Test
        void aCycleOfPropertiesInAnIdIsNamedAndNoMirrorIsBypassed() throws IOException {
            f.settings(
                            """
                            <settings><mirrors><mirror>
                              <id>m</id><mirrorOf>*,!default</mirrorOf>
                              <url>https://mirror.example.org/</url>
                            </mirror></mirrors></settings>
                            """)
                    .pom(
                            "pom.xml",
                            project(
                                    "child",
                                    "  <properties><a>${b}</a><b>${a}</b></properties>\n"
                                            + repository("${a}", "https://other.example.org/m")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("an id Maven could not fill in is not the id default")
                    .doesNotContain("other.example.org");
            assertThat(answer.unknowns())
                    .as("the builder's problem is named")
                    .anyMatch(u -> u.why().contains("cycle") || u.why().contains("recursive"));
        }

        @Test
        void aPomReachingItselfThroughLinksIsReadOnce() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "root",
                            repository("r", "https://r.example.org/m")
                                    + "  <modules><module>a</module><module>b</module></modules>\n"));
            Files.createSymbolicLink(f.checkout.resolve("a"), Path.of("."));
            Files.createSymbolicLink(f.checkout.resolve("b"), Path.of("."));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.unknowns())
                    .as("the limit on reads is not what stops it")
                    .noneMatch(u -> u.why().contains("poms were read"));
            assertThat(hosts(answer)).as("and it answers").contains("r.example.org");
        }
    }

    /** Harm prevented: the person's own settings are misread, and their override is lost. */
    @Nested
    class SettingsElementsNamedLikeOthers {
        @Test
        void aPropertyNamedProfileDoesNotEndTheProfile() throws IOException {
            settingsProfile(
                            """
                            <properties>
                              <profile>dev</profile>
                              <repo.host>settings.example.org</repo.host>
                            </properties>
                            """)
                    .pom(
                            "pom.xml",
                            project(
                                    "child",
                                    properties("repo.host", "other.example.org")
                                            + repository("r", "https://${repo.host}/m")));

            assertThat(hosts(f.answer()))
                    .as("the person's value still wins")
                    .contains("settings.example.org")
                    .doesNotContain("other.example.org");
        }

        @Test
        void aPropertyNamedMirrorOrRepositoryIsAProperty() throws IOException {
            settingsProfile(
                            """
                            <properties>
                              <mirror>m</mirror>
                              <repository>r</repository>
                              <repo.host>settings.example.org</repo.host>
                            </properties>
                            """
                                    + repository("corp", "https://${repo.host}/m"))
                    .pom("pom.xml", project("child", ""));

            assertThat(hosts(f.answer()))
                    .as("the profile's repository stays, filled in by the person's value")
                    .contains("settings.example.org");
        }

        @Test
        void aServerInAServersConfigurationIsNoServer() throws IOException {
            f.settings(
                            """
                            <settings><servers><server>
                              <id>real</id>
                              <configuration><server><id>inner</id></server></configuration>
                            </server></servers></settings>
                            """)
                    .pom(
                            "pom.xml",
                            project("child", repository("inner", "https://r.example.org/")));

            assertThat(host(f.answer(), "r.example.org").server().known())
                    .as("no credential is claimed for an id no server has")
                    .isFalse();
        }

        @Test
        void aMirrorForOtherLayoutsLeavesARepositoryAlone() throws IOException {
            f.settings(
                            """
                            <settings><mirrors><mirror>
                              <id>m</id><mirrorOf>*</mirrorOf><mirrorOfLayouts>p2</mirrorOfLayouts>
                              <url>https://mirror.example.org/</url>
                            </mirror></mirrors></settings>
                            """)
                    .pom("pom.xml", project("child", repository("r", "https://r.example.org/m")));

            assertThat(hosts(f.answer()))
                    .as("the repository's default layout is not among them")
                    .contains("r.example.org")
                    .doesNotContain("mirror.example.org");
        }
    }

    /** Harm prevented: the person is sent to the wrong file, or to none, for what is unknown. */
    @Nested
    class WhatIsUnknownNamesItsPlace {
        @Test
        void aProfileWithoutAnIdNamesItsPom() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                              <profiles>
                                <profile>
                                  <activation><jdk>21</jdk></activation>
                                  <repositories>
                                    <repository><id>r</id><url>https://jdk.example.org/m</url></repository>
                                  </repositories>
                                </profile>
                              </profiles>
                            """));

            assertThat(f.answer().unknowns())
                    .as("the checkout's, with its file and line")
                    .anyMatch(
                            u ->
                                    u.what().startsWith("profile")
                                            && u.place().file().equals("pom.xml")
                                            && u.place().line() > 0
                                            && u.place().origin() == Origin.CHECKOUT);
        }

        @Test
        void aRepositoryWithoutAUrlNamesItsPom() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "  <repositories><repository><id>r</id></repository></repositories>\n"));

            assertThat(f.answer().unknowns())
                    .as("the checkout's, with its file and line")
                    .anyMatch(
                            u ->
                                    u.what().equals("repository r")
                                            && u.place().file().equals("pom.xml")
                                            && u.place().line() > 0
                                            && u.place().origin() == Origin.CHECKOUT);
        }

        @Test
        void aMissingModuleNamesItsLine() throws IOException {
            f.pom("pom.xml", project("child", "  <modules><module>gone</module></modules>\n"));

            assertThat(f.answer().unknowns())
                    .as("the module's own line")
                    .anyMatch(u -> u.what().equals("module gone") && u.place().line() > 0);
        }

        @Test
        void aPomThatCannotBeParsedDoesNotNameTheMachinesPath() throws IOException {
            f.pom("pom.xml", "<project><notClosed></project>");

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.unknowns()).as("it is named").isNotEmpty();
            assertThat(answer.toString())
                    .as("in the file's own terms")
                    .doesNotContain(f.checkout.toString());
        }
    }

    /** Harm prevented: the person's own settings are read differently from how Maven reads them. */
    @Nested
    class SettingsAsMavenReadsThem {
        @ParameterizedTest
        @ValueSource(strings = {"<localRepository/>", "<localRepository>  </localRepository>"})
        void anEmptyLocalRepositoryIsTheDefault(String element) throws IOException {
            f.settings("<settings>" + element + "</settings>")
                    .localPom(
                            "org.example",
                            "grand",
                            "1",
                            project("grand", repository("r", "https://local.example.org/m")))
                    .pom("pom.xml", project("child", parent("grand", "")));

            assertThat(hosts(f.answer()))
                    .as("the parent is found where Maven looks, not where the process runs")
                    .contains("local.example.org");
        }

        @Test
        void aRepositoryCoveredByAMirrorIsReplacedThoughItsUrlCannotBeRead() throws IOException {
            f.settings(
                            """
                            <settings><mirrors><mirror>
                              <id>corp</id><mirrorOf>snapshots</mirrorOf>
                              <url>https://mirror.example.org/s</url>
                            </mirror></mirrors></settings>
                            """)
                    .pom(
                            "pom.xml",
                            project("child", repository("snapshots", "${env.SNAPSHOT_URL}")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("a mirror is selected by the id alone")
                    .contains("mirror.example.org");
            assertThat(answer.unknowns())
                    .as("so nothing is missing for the URL it replaces")
                    .noneMatch(u -> u.what().contains("SNAPSHOT_URL"));
        }

        @Test
        void aSettingsRepositorysLayoutDecidesItsMirror() throws IOException {
            f.settings(
                            """
                            <settings>
                              <mirrors><mirror>
                                <id>m</id><mirrorOf>*</mirrorOf>
                                <mirrorOfLayouts>default</mirrorOfLayouts>
                                <url>https://mirror.example.org/</url>
                              </mirror></mirrors>
                              <profiles><profile>
                                <id>corp</id>
                                <repositories><repository>
                                  <id>p2-repo</id><layout>p2</layout>
                                  <url>https://p2.example.org/repo</url>
                                </repository></repositories>
                              </profile></profiles>
                              <activeProfiles><activeProfile>corp</activeProfile></activeProfiles>
                            </settings>
                            """)
                    .pom("pom.xml", project("child", ""));

            assertThat(hosts(f.answer()))
                    .as("another layout goes straight to its host")
                    .contains("p2.example.org");
        }
    }

    /** Harm prevented: a common pom is refused. */
    @Test
    void theRootDirectoryOutsideARepositoryRefusesNothing() throws IOException {
        f.pom(
                "pom.xml",
                project(
                        "child",
                        "  <description>${project.rootDirectory}</description>\n"
                                + repository("r", "https://r.example.org/m")));

        RepositoryHostsAnswer answer = f.answer();

        assertThat(hosts(answer)).as("the pom is answered").contains("r.example.org");
        assertThat(answer.unknowns())
                .as("nothing refused")
                .noneMatch(u -> u.why().contains("refused"));
    }

    /** Harm prevented: a value is placed in the wrong file, through a name, a link or a file. */
    @Nested
    class WhereAValueStands {
        @Test
        void aDirectoryWithAColonInItsNameIsStillTheCheckouts() throws IOException {
            f.pom("pom.xml", project("root", "  <modules><module>x:</module></modules>\n"))
                    .pom(
                            "x:/pom.xml",
                            project("m", repository("r", "https://other.example.org/m")));

            RepositoryHost host = host(f.answer(), "other.example.org");

            assertThat(host.origin())
                    .as("a path holding a colon and a slash is no built-in source")
                    .isEqualTo(Origin.CHECKOUT);
            assertThat(host.place().file()).as("with its file").isEqualTo("x:/pom.xml");
        }

        @Test
        void settingsLinkedIntoTheCheckoutAreTheCheckouts() throws IOException {
            f.pom("pom.xml", project("child", ""));
            Path written =
                    Files.writeString(
                            f.checkout.resolve("other-settings.xml"),
                            """
                            <settings><mirrors><mirror>
                              <id>m</id><mirrorOf>*</mirrorOf>
                              <url>https://other.example.org/</url>
                            </mirror></mirrors></settings>
                            """);
            Files.createDirectories(f.home.resolve(".m2"));
            Files.createSymbolicLink(f.home.resolve(".m2/settings.xml"), written);

            assertThat(host(f.answer(), "other.example.org").origin())
                    .as("it stands in the checkout")
                    .isEqualTo(Origin.CHECKOUT);
        }
    }

    /** Harm prevented: the hosts of settings other than those the build reads are answered. */
    @Nested
    class SettingsNamedInMavenConfig {
        private static final String MIRROR =
                """
                <settings><mirrors><mirror>
                  <id>m</id><mirrorOf>*</mirrorOf><url>https://named.example.org/</url>
                </mirror></mirrors></settings>
                """;

        @Test
        void inTheCheckoutAreTheCheckouts() throws IOException {
            f.pom("ci/settings.xml", MIRROR)
                    .pom(".mvn/maven.config", "-s\nci/settings.xml\n")
                    .pom("pom.xml", project("child", ""));

            RepositoryHost host = host(f.answer(), "named.example.org");

            assertThat(host.origin()).as("it lies in the checkout").isEqualTo(Origin.CHECKOUT);
            assertThat(host.place().file()).as("with its file").isEqualTo("ci/settings.xml");
        }

        @Test
        void underTheHomeAreYours() throws IOException {
            Files.writeString(f.home.resolve("named.xml"), MIRROR);
            f.pom(".mvn/maven.config", "--settings\n" + f.home.resolve("named.xml") + "\n")
                    .pom("pom.xml", project("child", ""));

            RepositoryHost host = host(f.answer(), "named.example.org");

            assertThat(host.origin()).as("it lies under the home").isEqualTo(Origin.YOURS);
            assertThat(host.place().file()).as("relative to the home").isEqualTo("named.xml");
        }

        @Test
        void elsewhereAreTheMachinesWithAnAbsolutePath() throws IOException {
            Path elsewhere = Files.writeString(root.resolve("elsewhere.xml"), MIRROR);
            f.pom(".mvn/maven.config", "-s\n" + elsewhere + "\n")
                    .pom("pom.xml", project("child", ""));

            RepositoryHost host = host(f.answer(), "named.example.org");

            assertThat(host.origin()).as("neither home nor checkout").isEqualTo(Origin.MACHINE);
            assertThat(host.place().file())
                    .as("only there a path may be absolute")
                    .isEqualTo(elsewhere.toRealPath().toString());
        }

        @Test
        void onOneLineWithTheOptionAreAPathWithASpace() throws IOException {
            f.pom("ci/settings.xml", MIRROR)
                    .pom(".mvn/maven.config", "-s ci/settings.xml\n")
                    .pom("pom.xml", project("child", ""));

            assertThat(f.answer().unknowns())
                    .as("each line is one argument, and Maven stops on that path")
                    .anyMatch(u -> u.what().contains("ci/settings.xml"));
        }

        @Test
        void thatAreMissingAreNamed() throws IOException {
            f.pom(".mvn/maven.config", "-smissing.xml\n").pom("pom.xml", project("child", ""));

            assertThat(f.answer().unknowns())
                    .as("Maven stops on them, and the answer does not pretend they are empty")
                    .anyMatch(u -> u.what().contains("missing.xml"));
        }
    }

    /** Harm prevented: a local repository other than the build's fills a parent in. */
    @Test
    void aLocalRepositoryInMavenConfigIsWhereAParentIsFound() throws IOException {
        Path elsewhere = root.resolve("elsewhere-repository");
        Path parentPom = elsewhere.resolve("org/example/far/1/far-1.pom");
        Files.createDirectories(parentPom.getParent());
        Files.writeString(parentPom, project("far", repository("f", "https://far.example.org/m")));
        f.pom(".mvn/maven.config", "-Dmaven.repo.local=" + elsewhere + "\n")
                .pom("pom.xml", project("child", parent("far", "")));

        assertThat(hosts(f.answer())).as("the parent is read").contains("far.example.org");
    }

    /** Harm prevented: what the answer cannot follow in a file of options goes unsaid. */
    @ParameterizedTest
    @CsvSource({
        ".mvn/maven.config, -f",
        ".mvn/jvm.config, -javaagent:agent.jar",
    })
    void anOptionThatCannotBeFollowedIsNamed(String file, String option) throws IOException {
        f.pom(file, option + "\n").pom("pom.xml", project("child", ""));

        assertThat(f.answer().unplaced())
                .as("with its file and line")
                .anyMatch(
                        u ->
                                u.place().file().equals(file)
                                        && u.place().line() == 1
                                        && u.note().contains(option.split(" ")[0]));
    }

    /** Harm prevented: what the build ignores buries what the answer does name. */
    @ParameterizedTest
    @CsvSource({
        ".mvn/maven.config, -B",
        ".mvn/maven.config, -ntp",
        ".mvn/maven.config, -T1C",
        ".mvn/maven.config, -Daether.enhancedLocalRepository.split=true",
        ".mvn/jvm.config, --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        ".mvn/jvm.config, -Xmx2g",
    })
    void anOptionThatLeavesTheModelAloneIsNotNamed(String file, String option) throws IOException {
        f.pom(file, option + "\n").pom("pom.xml", project("child", ""));

        assertThat(f.answer().unplaced()).as("nothing to say about it").isEmpty();
    }

    /** Harm prevented: the real build differs from the answer, and nothing says why. */
    @Nested
    class ExtensionsThatAreNotRead {

        @Test
        void aManagedExtensionNothingUsesChangesNothing() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "  <build><pluginManagement><plugins><plugin><groupId>g</groupId>"
                                    + "<artifactId>e</artifactId><version>1</version>"
                                    + "<extensions>true</extensions></plugin></plugins>"
                                    + "</pluginManagement></build>\n"));

            assertThat(f.answer().unplaced()).as("no build uses it").isEmpty();
        }

        @Test
        void aManagedExtensionABuildUsesIsNamed() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "  <build><pluginManagement><plugins><plugin><groupId>g</groupId>"
                                    + "<artifactId>e</artifactId><version>1</version>"
                                    + "<extensions>true</extensions></plugin></plugins>"
                                    + "</pluginManagement><plugins><plugin><groupId>g</groupId>"
                                    + "<artifactId>e</artifactId></plugin></plugins></build>\n"));

            assertThat(f.answer().unplaced()).as("the build loads it").isNotEmpty();
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "<build><extensions><extension><groupId>g</groupId><artifactId>e</artifactId>"
                            + "<version>1</version></extension></extensions></build>",
                    "<build><plugins><plugin><groupId>g</groupId><artifactId>e</artifactId>"
                            + "<version>1</version><extensions>true</extensions></plugin></plugins>"
                            + "</build>",
                })
        void anExtensionInAPomIsNamed(String build) throws IOException {
            f.pom("pom.xml", project("child", "  " + build + "\n"));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.unplaced())
                    .as("and the pom is named with its line")
                    .anyMatch(u -> u.place().file().equals("pom.xml") && u.place().line() > 0);
        }

        @Test
        void anExtensionInAParentIsNamed() throws IOException {
            f.pom(
                            "parent/pom.xml",
                            project(
                                    "parent",
                                    "  <build><extensions><extension><groupId>g</groupId>"
                                            + "<artifactId>e</artifactId><version>1</version>"
                                            + "</extension></extensions></build>\n"))
                    .pom("pom.xml", project("child", parent("parent", "parent/pom.xml")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.unplaced())
                    .as("and the parent is named")
                    .anyMatch(u -> u.place().file().equals("parent/pom.xml"));
        }

        @Test
        void aPluginWithExtensionsOffChangesNothing() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "  <build><plugins><plugin><groupId>g</groupId><artifactId>e</artifactId>"
                                    + "<version>1</version><extensions>false</extensions></plugin>"
                                    + "</plugins></build>\n"));

            assertThat(f.answer().unplaced()).as("no extension is loaded").isEmpty();
        }
    }

    /** Harm prevented: the host's machine decides what the build in its box will reach. */
    @Nested
    class AProfileOnTheMachine {
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "<property><name>env.CI</name></property>",
                    "<property><name>!env.CI</name></property>",
                    "<property><name>java.version</name></property>",
                    "<jdk>[1,)</jdk>",
                    "<os><family>unix</family></os>",
                    "<file><exists>${user.home}/marker</exists></file>",
                    "<file><exists>../marker</exists></file>",
                    "<property><name>!turn.on</name></property><jdk>[1,)</jdk>",
                })
        void isNamedAndItsRepositoryIsNotAnswered(String condition) throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                              <profiles><profile>
                                <id>p</id>
                                <activation>%s</activation>
                                <repositories>
                                  <repository><id>r</id><url>https://p.example.org/m</url></repository>
                                </repositories>
                              </profile></profiles>
                            """
                                    .formatted(condition)));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer)).as("it stays off").doesNotContain("p.example.org");
            assertThat(answer.unknowns())
                    .as("and is named")
                    .anyMatch(u -> u.what().equals("profile p"));
        }

        @Test
        void aModuleBesideTheCheckoutIsRead() throws IOException {
            Files.createDirectories(root.resolve("beside"));
            Files.writeString(
                    root.resolve("beside/pom.xml"),
                    project("b", repository("b", "https://beside.example.org/m")));
            f.pom("pom.xml", project("root", "  <modules><module>../beside</module></modules>\n"));

            assertThat(hosts(f.answer()))
                    .as("as the build reads it")
                    .contains("beside.example.org");
        }
    }

    /** Harm prevented: a URL is shown other than as it is written. */
    @Nested
    class WhatIsShown {
        @Test
        void aUrlKeepsItsEncoding() throws IOException {
            f.pom(
                    "pom.xml",
                    project("child", repository("r", "https://a.example.org/%E2%80%AE/a%2Fb")));

            assertThat(host(f.answer(), "a.example.org").url())
                    .as("the URL as written, not as decoded")
                    .isEqualTo("https://a.example.org/%E2%80%AE/a%2Fb");
        }
    }

    /** Harm prevented: a refusal names the pom that asked, and not the pom it is about. */
    @Test
    void aParentRefusedByTheGuardIsTheOneNamed() throws IOException {
        String large = "<!-- " + "x".repeat(PomGuard.MAX_POM_BYTES) + " -->";
        f.localPom("org.example", "large", "1", large + project("large", ""))
                .pom("pom.xml", project("child", parent("large", "")));

        assertThat(f.answer().unknowns())
                .as("the guard's own refusal reaches the answer")
                .anyMatch(u -> u.what().endsWith("large-1.pom") && u.why().startsWith("refused"));
    }

    /** Harm prevented: a mirror is answered as certain while the build may go around it. */
    @Nested
    class AMirrorThatDependsOnWhatIsNotRead {
        private void mirrorOf(String pattern) throws IOException {
            f.settings(
                    """
                    <settings><mirrors><mirror>
                      <id>m</id><mirrorOf>%s</mirrorOf><url>https://mirror.example.org/</url>
                    </mirror></mirrors></settings>
                    """
                            .formatted(pattern));
        }

        @Test
        void isAnsweredWithAnUnknownWhenItsPatternAsksTheUrl() throws IOException {
            mirrorOf("external:*");
            f.pom("pom.xml", project("child", repository("r", "${env.LOCAL}")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer)).as("the mirror").contains("mirror.example.org");
            assertThat(answer.unknowns())
                    .as("and what could send the build elsewhere")
                    .anyMatch(u -> u.what().equals("${env.LOCAL}"));
        }

        @Test
        void isAnsweredWithAnUnknownWhenItsPatternAsksAnIdNotRead() throws IOException {
            mirrorOf("*,!internal");
            f.pom("pom.xml", project("child", repository("${env.REPO}", "https://r.example.org/")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer)).as("the mirror").contains("mirror.example.org");
            assertThat(answer.unknowns())
                    .as("and what could send the build elsewhere")
                    .anyMatch(u -> u.what().equals("${env.REPO}"));
        }

        @Test
        void isCertainWhenItsPatternAsksNothing() throws IOException {
            mirrorOf("*");
            f.pom("pom.xml", project("child", repository("${env.REPO}", "${env.LOCAL}")));

            assertThat(f.answer().unknowns())
                    .as("every repository goes to the mirror, whatever these hold")
                    .noneMatch(u -> u.what().contains("env."));
        }
    }

    /** Harm prevented: a value Maven fills in from the machine disappears without a word. */
    @Nested
    class SettingsThatNeedTheMachine {
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "<mirrors><mirror><id>m</id><mirrorOf>${env.X}</mirrorOf>"
                            + "<url>https://m.example.org/</url></mirror></mirrors>",
                    "<activeProfiles><activeProfile>${env.X}</activeProfile></activeProfiles>",
                    "<localRepository>${env.X}/r</localRepository>",
                })
        void isNamed(String element) throws IOException {
            f.settings("<settings>" + element + "</settings>").pom("pom.xml", project("child", ""));

            assertThat(f.answer().unknowns())
                    .as("Maven fills it in, and nothing here does")
                    .anyMatch(
                            u ->
                                    u.what().contains("${env.X}")
                                            && u.place().file().equals(".m2/settings.xml")
                                            && u.place().line() > 0);
        }

        @Test
        void aMirrorWithoutAUrlIsNamed() throws IOException {
            f.settings(
                            "<settings><mirrors><mirror><id>m</id><mirrorOf>*</mirrorOf></mirror>"
                                    + "</mirrors></settings>")
                    .pom("pom.xml", project("child", ""));

            assertThat(f.answer().unknowns())
                    .as("a mirror of everything is not dropped silently")
                    .anyMatch(u -> u.what().equals("mirror m"));
        }

        @Test
        void aMirrorOfNothingNamedIsNamed() throws IOException {
            f.settings(
                            "<settings><mirrors><mirror><id>m</id>"
                                    + "<url>https://m.example.org/</url></mirror></mirrors></settings>")
                    .pom("pom.xml", project("child", ""));

            assertThat(f.answer().unknowns())
                    .as("Maven refuses such settings")
                    .anyMatch(u -> u.what().equals("mirror m"));
        }
    }

    /** Harm prevented: a person is asked to open a host the build never reaches. */
    @Nested
    class WhatTheBuildNeverReaches {
        @Test
        void aRepositoryWithBothPoliciesOffIsNoHost() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                              <repositories><repository>
                                <id>r</id><url>https://off.example.org/m</url>
                                <releases><enabled>false</enabled></releases>
                                <snapshots><enabled>false</enabled></snapshots>
                              </repository></repositories>
                            """));

            assertThat(hosts(f.answer())).as("nothing asks it").doesNotContain("off.example.org");
        }

        @Test
        void aSettingsRepositoryWithBothPoliciesOffIsNoHost() throws IOException {
            f.settings(
                            """
                            <settings><profiles><profile><id>p</id>
                              <activation><activeByDefault>true</activeByDefault></activation>
                              <repositories><repository>
                                <id>r</id><url>https://off.example.org/m</url>
                                <releases><enabled>false</enabled></releases>
                                <snapshots><enabled>false</enabled></snapshots>
                              </repository></repositories>
                            </profile></profiles></settings>
                            """)
                    .pom("pom.xml", project("child", ""));

            assertThat(hosts(f.answer())).as("nothing asks it").doesNotContain("off.example.org");
        }

        @Test
        void aRepositoryWithOnePolicyOnIsAHost() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                              <repositories><repository>
                                <id>r</id><url>https://on.example.org/m</url>
                                <releases><enabled>false</enabled></releases>
                              </repository></repositories>
                            """));

            assertThat(hosts(f.answer())).as("snapshots still ask it").contains("on.example.org");
        }

        @Test
        void aBlockedMirrorIsNamedAndNoHost() throws IOException {
            f.settings(
                            """
                            <settings><mirrors><mirror>
                              <id>m</id><mirrorOf>*</mirrorOf><blocked>true</blocked>
                              <url>https://blocked.example.org/</url>
                            </mirror></mirrors></settings>
                            """)
                    .pom("pom.xml", project("child", ""));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("it is never reached")
                    .doesNotContain("blocked.example.org");
            assertThat(answer.unknowns()).as("and said").anyMatch(u -> u.why().contains("blocks"));
        }
    }

    /** Harm prevented: a repository the person's mirror covers is answered as reached directly. */
    @Nested
    class ARepositorysUrlAsMavenTakesItApart {
        private final Mirrors external =
                new Mirrors(
                        List.of(
                                new UserSettings.Mirror(
                                        "m",
                                        "https://m.example.org/",
                                        "external:*",
                                        "*",
                                        false,
                                        new Place("", 0, Origin.MACHINE))));
        private final Mirrors externalHttp =
                new Mirrors(
                        List.of(
                                new UserSettings.Mirror(
                                        "m",
                                        "https://m.example.org/",
                                        "external:http:*",
                                        "*",
                                        false,
                                        new Place("", 0, Origin.MACHINE))));

        @ParameterizedTest
        @ValueSource(strings = {" https://other.example.org/m", "https://LOCALHOST/m"})
        void aUrlMavenTakesForExternalIsMirrored(String url) {
            assertThat(external.of("r", url, "default"))
                    .as("as Maven's own pattern takes it apart")
                    .isPresent();
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://localhost/m", "https://127.0.0.1/m", "file:///m"})
        void aLocalUrlIsNotExternal(String url) {
            assertThat(external.of("r", url, "default")).as("local stays direct").isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"http://a.example.org/", "dav:http://a.example.org/", "DAV://a/"})
        void everyHttpProtocolOfMavensIsExternalHttp(String url) {
            assertThat(externalHttp.of("r", url, "default")).as("as Maven lists them").isPresent();
        }
    }

    /** Harm prevented: each refused pom keeps a file open, for as long as the plugin runs. */
    @Test
    void aPomRefusedAtTheGuardHasItsStreamClosed() throws IOException {
        Path outside = Files.writeString(root.resolve("outside.xml"), project("o", ""));
        Files.createDirectories(f.localRepository());
        Places places = new Places(f.checkout, f.home, f.home.resolve(".m2/settings.xml"));
        ModelProcessor unused = new DefaultModelBuilderFactoryProbe().processor();
        PomGuard guard = new PomGuard(unused, places);
        AtomicBoolean closed = new AtomicBoolean();
        var in =
                new ByteArrayInputStream(new byte[0]) {
                    @Override
                    public void close() throws IOException {
                        closed.set(true);
                        super.close();
                    }
                };

        assertThatThrownBy(
                        () ->
                                guard.read(
                                        in,
                                        Map.of(
                                                ModelProcessor.SOURCE,
                                                new FileModelSource(outside.toFile()))))
                .as("nothing of it can be read")
                .hasMessageContaining("refused");

        assertThat(closed).as("the builder leaves closing to its reader").isTrue();
    }

    private static final class DefaultModelBuilderFactoryProbe extends DefaultModelBuilderFactory {
        ModelProcessor processor() {
            return newModelProcessor();
        }
    }
}
