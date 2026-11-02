package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.porelo.ecosystem.maven.Fixture.parent;
import static org.fuin.porelo.ecosystem.maven.Fixture.project;
import static org.fuin.porelo.ecosystem.maven.Fixture.properties;
import static org.fuin.porelo.ecosystem.maven.Fixture.repository;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

class HostileValuesTest {
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

    /** Harm prevented: a forge gets a host of its choice marked as the person's own. */
    @Nested
    class AHostChosenByTheCheckout {
        @Test
        void isNotHiddenByALeadingDotInTheExpression() throws IOException {
            settingsProfile(repository("corp-repo", "https://${.repo.host}/maven"))
                    .pom("pom.xml", project("child", properties("repo.host", "evil.example.org")));

            assertThat(host(f.answer(), "evil.example.org").origin())
                    .as("the builder drops the dot, and so must the tracing")
                    .isEqualTo(Origin.CHECKOUT);
        }

        @Test
        void isNotThePersonsByAPropertyNamedLikeAProjectField() throws IOException {
            settingsProfile(
                            properties("project.name", "corp.example.org")
                                    + repository("corp-repo", "https://${project.name}/maven"))
                    .pom("pom.xml", project("child", "  <name>evil.example.org</name>\n"));

            assertThat(host(f.answer(), "evil.example.org").origin())
                    .as("the model's field wins over a property of its name")
                    .isEqualTo(Origin.CHECKOUT);
        }

        @Test
        void isNotThePersonsWhenASettingsPluginRepositorySharesARepositorysId() throws IOException {
            settingsProfile(
                            repository("corp", "https://corp.example.org/releases")
                                    + """
                                      <pluginRepositories>
                                        <pluginRepository>
                                          <id>corp</id>
                                          <url>https://${plugin.host}/plugins</url>
                                        </pluginRepository>
                                      </pluginRepositories>
                                    """)
                    .pom(
                            "pom.xml",
                            project("child", properties("plugin.host", "evil.example.org")));

            assertThat(host(f.answer(), "evil.example.org").origin())
                    .as("the raw URL is found by its place, not by a shared id")
                    .isEqualTo(Origin.CHECKOUT);
        }

        @Test
        void isNotTheMachinesWhenTheParentsVersionIsItselfAProperty() throws IOException {
            f.localPom(
                            "org.example",
                            "versioned",
                            "1",
                            """
                            <project>
                              <modelVersion>4.0.0</modelVersion>
                              <groupId>org.example</groupId>
                              <artifactId>versioned</artifactId>
                              <version>${revision}</version>
                              <packaging>pom</packaging>
                              <properties><revision>1</revision></properties>
                            %s
                            </project>
                            """
                                    .formatted(repository("r", "https://${repo.host}/maven")))
                    .pom(
                            "pom.xml",
                            project(
                                    "child",
                                    parent("versioned", "")
                                            + properties("repo.host", "evil.example.org")));

            assertThat(host(f.answer(), "evil.example.org").origin())
                    .as("the raw model is found though its id holds an expression")
                    .isEqualTo(Origin.CHECKOUT);
        }

        @Test
        void isNotThePersonsWhenTheLaterOfTwoActiveProfilesFillsItIn() throws IOException {
            MergeFixtures.laterSettingsProfileWins(f);

            assertThat(host(f.answer(), "evil.example.org").origin())
                    .as("the later profile wins, and its value came from the checkout")
                    .isEqualTo(Origin.CHECKOUT);
        }

        @Test
        void namesTheLaterOfTwoActivePomProfilesAsWhereThePropertyStands() throws IOException {
            String pom =
                    project(
                            "child",
                            """
                              <profiles>
                                <profile>
                                  <id>a</id>
                                  <properties><repo.host>a.example.org</repo.host></properties>
                                </profile>
                                <profile>
                                  <id>b</id>
                                  <properties><repo.host>b.example.org</repo.host></properties>
                                </profile>
                              </profiles>
                            """
                                    + repository("r", "https://${repo.host}/maven"));
            f.settings(
                            """
                            <settings><activeProfiles>
                              <activeProfile>a</activeProfile>
                              <activeProfile>b</activeProfile>
                            </activeProfiles></settings>
                            """)
                    .pom("pom.xml", pom);
            int lineOfB = 1;
            for (String line : pom.split("\n")) {
                if (line.contains("b.example.org")) {
                    break;
                }
                lineOfB++;
            }
            int expected = lineOfB;

            assertThat(host(f.answer(), "b.example.org").properties())
                    .as("the property's place is the winner's")
                    .extracting(u -> u.place().line())
                    .containsExactly(expected);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "https://${project.organization.name}/maven",
                    "https://${project.scm.url}/maven",
                    "https://${pom.organization.name}/maven",
                })
        void isUnknownWhenItComesFromAFieldThatCannotBeTraced(String url) throws IOException {
            settingsProfile(repository("corp-repo", url))
                    .pom(
                            "pom.xml",
                            project(
                                    "child",
                                    """
                                      <organization><name>evil.example.org</name></organization>
                                      <scm><url>evil.example.org</url></scm>
                                    """));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.hosts())
                    .as("a value whose writer cannot be named is no host of anyone's")
                    .noneMatch(h -> h.url().contains("evil"));
            assertThat(answer.unknowns())
                    .as("the expression named, not dropped")
                    .anyMatch(u -> u.what().startsWith("${") && u.why().contains("corp-repo"));
        }

        @Test
        void isUnknownWhenItComesFromAMissingParentsCoordinates() throws IOException {
            settingsProfile(repository("corp-repo", "https://${project.groupId}/maven"))
                    .pom(
                            "pom.xml",
                            """
                            <project>
                              <modelVersion>4.0.0</modelVersion>
                              <parent>
                                <groupId>evil.example.org</groupId>
                                <artifactId>missing</artifactId>
                                <version>1</version>
                                <relativePath/>
                              </parent>
                              <artifactId>child</artifactId>
                            </project>
                            """);

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("its coordinates stand in the parent element, which names no field's place")
                    .doesNotContain("evil.example.org");
            assertThat(answer.unknowns())
                    .as("named, not dropped")
                    .anyMatch(u -> u.why().contains("corp-repo"));
        }

        @Test
        void isUnknownWhenItComesFromTheBuildTimestamp() throws IOException {
            settingsProfile(repository("corp-repo", "https://${build.timestamp}/maven"))
                    .pom(
                            "pom.xml",
                            project(
                                    "child",
                                    properties(
                                            "maven.build.timestamp.format", "'evil.example.org'")));

            assertThat(f.answer().hosts())
                    .as("the format is the checkout's, and the host must not be the person's")
                    .noneMatch(h -> h.host().equals("evil.example.org"));
        }
    }

    /** Harm prevented: where the person works reaches a forge inside what looks like a URL. */
    @Nested
    class TheMachinesPath {
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "https:/${project.basedir}",
                    "https://a.example.org${project.basedir}",
                    "https://a.example.org${pom.basedir}",
                    "https://a.example.org${basedir}",
                    "https://a.example.org/${project.baseUri}",
                    "https://a.example.org/${project.rootDirectory}",
                    "https://a.example.org/${project.projectDirectory}",
                    "https://a.example.org/${project.pomFile}",
                    "https://a.example.org/${project.pomFile.parent}",
                })
        void neverReachesAUrl(String url) throws IOException {
            f.pom("pom.xml", project("child", repository("r", url)));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.toString())
                    .as("no part of the checkout's path")
                    .doesNotContain(f.checkout.toString());
            assertThat(answer.hosts())
                    .as("and what depends on it is no host")
                    .noneMatch(h -> h.id().equals("r"));
        }

        @Test
        void neverReachesAUrlThroughAProperty() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("where", "${project.basedir}")
                                    + repository("r", "https://a.example.org${where}")));

            assertThat(f.answer().toString())
                    .as("no part of the checkout's path")
                    .doesNotContain(f.checkout.toString());
        }

        @Test
        void neverReachesAnId() throws IOException {
            f.pom(
                    "pom.xml",
                    project("child", repository("${project.basedir}", "https://a.example.org/m")));

            assertThat(f.answer().toString())
                    .as("no part of the checkout's path")
                    .doesNotContain(f.checkout.toString());
        }
    }

    /** Harm prevented: one hostile pom takes the whole answer down, or the machine with it. */
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
        void propertiesThatDoubleBehindALeadingDotAreRefused() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "bomb",
                            "  <properties>"
                                    + doubling("${.p%d}")
                                    + "</properties>\n"
                                    + repository("r", "https://${.p40}.example.org/")));

            assertThat(f.answer().unknowns())
                    .as("refused by the bound, not by running out of memory")
                    .anyMatch(u -> u.why().contains("would grow"));
        }

        @Timeout(120)
        @Test
        void valuesThatDoubleThroughAProjectFieldAreRefused() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "bomb",
                            "  <description>${p40}</description>\n  <properties>"
                                    + doubling("${p%d}")
                                    + "<q0>${project.description}${project.description}</q0>"
                                    + "</properties>\n"
                                    + repository("r", "https://${q0}.example.org/")));

            assertThat(f.answer().unknowns())
                    .as("refused by the bound, not by running out of memory")
                    .anyMatch(u -> u.why().contains("would grow"));
        }

        @Timeout(120)
        @Test
        void manyReferencesToALargeValueAreRefused() throws IOException {
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
                    .as("refused by the bound, not by running out of memory")
                    .anyMatch(u -> u.why().contains("would grow"));
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
                                            + repository("${a}", "https://evil.example.org/m")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("an id Maven could not fill in is not the id default")
                    .doesNotContain("evil.example.org");
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
                                    properties("repo.host", "evil.example.org")
                                            + repository("r", "https://${repo.host}/m")));

            assertThat(hosts(f.answer()))
                    .as("the person's value still wins")
                    .contains("settings.example.org")
                    .doesNotContain("evil.example.org");
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

        @Test
        void aRootPomLinkedOutsideNamesItsFile() throws IOException {
            Path outside = Files.writeString(root.resolve("outside.xml"), project("o", ""));
            Files.createSymbolicLink(f.checkout.resolve("pom.xml"), outside);

            assertThat(f.answer().unknowns())
                    .as("only the machine's values may stand without a file")
                    .anyMatch(u -> u.place().file().equals("pom.xml"));
        }
    }

    /** Harm prevented: a trace that does not reproduce the value names the wrong writer. */
    @Nested
    class ATraceMustReproduceTheValue {
        @Test
        void aPropertyReferringToItselfCannotPassAsThePersons() throws IOException {
            settingsProfile(
                            properties("name", "${name}")
                                    + repository("corp-repo", "https://${name}/maven"))
                    .pom("pom.xml", project("child", "  <name>evil.example.org</name>\n"));

            assertThat(f.answer().hosts())
                    .as("Maven passes over such a property, to the checkout's field")
                    .noneMatch(
                            h ->
                                    h.host().equals("evil.example.org")
                                            && h.origin() != Origin.CHECKOUT);
        }

        @Test
        void aChildSharingItsParentsIdCannotPassAsTheMachines() throws IOException {
            f.localPom(
                            "org.example",
                            "parent",
                            "1",
                            project(
                                    "parent",
                                    properties("repo.host", "trusted.example.org")
                                            + repository("r", "https://${repo.host}/maven")))
                    .pom(
                            "pom.xml",
                            """
                            <project>
                              <modelVersion>4.0.0</modelVersion>
                              <parent>
                                <groupId>org.example</groupId>
                                <artifactId>parent</artifactId>
                                <version>1</version>
                                <relativePath/>
                              </parent>
                              <artifactId>parent</artifactId>
                              <properties><repo.host>evil.example.org</repo.host></properties>
                            %s
                            </project>
                            """
                                    .formatted(
                                            repository(
                                                    "leak",
                                                    "https://leak.example.org${project.basedir}")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.hosts())
                    .as("the builder keeps one raw model under the shared id")
                    .noneMatch(
                            h ->
                                    h.host().equals("evil.example.org")
                                            && h.origin() != Origin.CHECKOUT);
            assertThat(answer.toString())
                    .as("and a repository whose raw form is lost is not answered")
                    .doesNotContain(f.checkout.toString());
        }

        @Test
        void aPropertyInAChildWithoutANameIsTheCheckoutsThoughItsParentHasOne() throws IOException {
            f.localPom(
                            "org.example",
                            "grand",
                            "1",
                            project(
                                    "grand",
                                    "  <name>Parent</name>\n"
                                            + repository("r", "https://${project.name}/maven")))
                    .pom(
                            "pom.xml",
                            project(
                                    "child",
                                    parent("grand", "")
                                            + properties("project.name", "evil.example.org")));

            assertThat(host(f.answer(), "evil.example.org").origin())
                    .as("a name is not inherited, so the checkout's property fills it")
                    .isEqualTo(Origin.CHECKOUT);
        }

        @Test
        void aPropertyUnderTheProjectPrefixIsAnswered() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("project.repo.host", "repo.example.org")
                                    + repository("r", "https://${project.repo.host}/maven")));

            assertThat(hosts(f.answer()))
                    .as("the model has no such field, so a harmless property fills it")
                    .contains("repo.example.org");
        }

        @Test
        void aPathBuiltOutOfPiecesNeverReachesAUrl() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "  <properties><d>$</d><where>${d}{project.basedir}</where></properties>\n"
                                    + repository("r", "https://a.example.org${where}")));

            assertThat(f.answer().toString())
                    .as("no part of the checkout's path")
                    .doesNotContain(f.checkout.toString());
        }

        @Test
        void aPathBehindASelfReferringPropertyNeverReachesAUrl() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "  <name>${project.basedir}</name>\n"
                                    + properties("name", "${name}")
                                    + repository("r", "https://a.example.org${name}")));

            assertThat(f.answer().toString())
                    .as("no part of the checkout's path")
                    .doesNotContain(f.checkout.toString());
        }

        @Test
        void aPathInAPropertyOfAChildWithoutANameNeverReachesAUrl() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            properties("project.name", "${project.basedir}")
                                    + repository("r", "https://a.example.org${project.name}")));

            assertThat(f.answer().toString())
                    .as("no part of the checkout's path")
                    .doesNotContain(f.checkout.toString());
        }
    }

    /** Harm prevented: a checkout makes a pom of the machine's speak as one of its modules. */
    @Nested
    class AModuleLinkedOutOfTheCheckout {
        @Test
        void isNotReadFromTheLocalRepository() throws IOException {
            f.localPom(
                    "org.example",
                    "elsewhere",
                    "1",
                    project("elsewhere", repository("o", "https://local.example.org/m")));
            Path local = f.localRepository().resolve("org/example/elsewhere/1/elsewhere-1.pom");
            f.pom("pom.xml", project("root", "  <modules><module>sub</module></modules>\n"));
            Files.createDirectories(f.checkout.resolve("sub"));
            Files.createSymbolicLink(f.checkout.resolve("sub/pom.xml"), local);

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer)).as("nothing from it").doesNotContain("local.example.org");
            assertThat(answer.unknowns())
                    .as("and named")
                    .anyMatch(u -> u.what().equals("module sub"));
        }
    }

    /** Harm prevented: a short pom holds the plugin on a CPU, with no way out for the caller. */
    @Nested
    class AnActivationThatDoubles {
        private static String levels() {
            StringBuilder levels = new StringBuilder("<p0>xxxxxxxxxxxxxxxx</p0>");
            for (int i = 1; i <= 28; i++) {
                levels.append("<p%d>${p%d}${p%d}</p%d>".formatted(i, i - 1, i - 1, i));
            }
            return levels.toString();
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "<jdk>${p28}</jdk>",
                    "<property><name>${p28}</name></property>",
                    "<os><name>${p28}</name></os>",
                    "<file><exists>${p28}</exists></file>",
                })
        void endsAndIsNamed(String activation) throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "bomb",
                            "  <properties>"
                                    + levels()
                                    + "</properties>\n  <profiles><profile><id>p</id><activation>"
                                    + activation
                                    + "</activation>"
                                    + repository("r", "https://r.example.org/m")
                                    + "</profile></profiles>\n"));

            RepositoryHostsAnswer answer =
                    assertTimeoutPreemptively(Duration.ofSeconds(20), () -> f.answer());

            assertThat(answer.unknowns())
                    .as("refused by the bound, and not after running out of memory first")
                    .anyMatch(u -> u.why().contains("would grow"));
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

    /** Harm prevented: a common pom is refused, or the walk looks above the checkout. */
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

    /** Harm prevented: a checkout's value loses its writer through a name, a link or a file. */
    @Nested
    class WhoseAValueIs {
        @Test
        void aDirectoryWithAColonInItsNameIsStillTheCheckouts() throws IOException {
            f.pom("pom.xml", project("root", "  <modules><module>x:</module></modules>\n"))
                    .pom("x:/pom.xml", project("m", repository("r", "https://evil.example.org/m")));

            RepositoryHost host = host(f.answer(), "evil.example.org");

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
                            f.checkout.resolve("forged-settings.xml"),
                            """
                            <settings><mirrors><mirror>
                              <id>m</id><mirrorOf>*</mirrorOf>
                              <url>https://evil.example.org/</url>
                            </mirror></mirrors></settings>
                            """);
            Files.createDirectories(f.home.resolve(".m2"));
            Files.createSymbolicLink(f.home.resolve(".m2/settings.xml"), written);

            assertThat(host(f.answer(), "evil.example.org").origin())
                    .as("whoever can push wrote it")
                    .isEqualTo(Origin.CHECKOUT);
        }
    }

    /** Harm prevented: the answer calls itself trusted while the real build can differ from it. */
    @Nested
    class WhatTheCheckoutCanChangeUnread {
        @ParameterizedTest
        @CsvSource({
            ".mvn/maven.config, -Drepo.url=https://evil.example.org/",
            ".mvn/maven.config, -s forged.xml",
            ".mvn/maven.config, -Pforged",
            ".mvn/jvm.config, -Dmaven.repo.central=https://evil.example.org/",
            ".mvn/jvm.config, -javaagent:forged.jar",
        })
        void aFileOfOptionsMakesTheAnswerUntrusted(String file, String options) throws IOException {
            f.pom("pom.xml", project("child", ""));
            Files.createDirectories(f.checkout.resolve(".mvn"));
            Files.writeString(f.checkout.resolve(file), options + "\n");

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.modelTrusted()).as("it can set any property").isFalse();
            assertThat(answer.unplaced())
                    .as("and it is named")
                    .anyMatch(u -> u.place().file().equals(file));
        }

        @ParameterizedTest
        @CsvSource({
            ".mvn/maven.config, -Daether.enhancedLocalRepository.split=true",
            ".mvn/jvm.config, --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            ".mvn/jvm.config, -Xmx2g",
        })
        void aFileOfOptionsThatCannotTouchTheModelChangesNothing(String file, String options)
                throws IOException {
            f.pom("pom.xml", project("child", ""));
            Files.createDirectories(f.checkout.resolve(".mvn"));
            Files.writeString(f.checkout.resolve(file), options + "\n");

            assertThat(f.answer().modelTrusted())
                    .as("every option in it is one that leaves the model alone")
                    .isTrue();
        }

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

            assertThat(f.answer().modelTrusted()).as("no build uses it").isTrue();
        }

        @Test
        void aManagedExtensionABuildUsesMakesTheAnswerUntrusted() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            "  <build><pluginManagement><plugins><plugin><groupId>g</groupId>"
                                    + "<artifactId>e</artifactId><version>1</version>"
                                    + "<extensions>true</extensions></plugin></plugins>"
                                    + "</pluginManagement><plugins><plugin><groupId>g</groupId>"
                                    + "<artifactId>e</artifactId></plugin></plugins></build>\n"));

            assertThat(f.answer().modelTrusted()).as("the build loads it").isFalse();
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
        void anExtensionInAPomMakesTheAnswerUntrusted(String build) throws IOException {
            f.pom("pom.xml", project("child", "  " + build + "\n"));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.modelTrusted()).as("its code can change the model").isFalse();
            assertThat(answer.unplaced())
                    .as("and the pom is named with its line")
                    .anyMatch(u -> u.place().file().equals("pom.xml") && u.place().line() > 0);
        }

        @Test
        void anExtensionInAParentMakesTheAnswerUntrusted() throws IOException {
            f.pom(
                            "parent/pom.xml",
                            project(
                                    "parent",
                                    "  <build><extensions><extension><groupId>g</groupId>"
                                            + "<artifactId>e</artifactId><version>1</version>"
                                            + "</extension></extensions></build>\n"))
                    .pom("pom.xml", project("child", parent("parent", "parent/pom.xml")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.modelTrusted()).as("every level counts").isFalse();
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

            assertThat(f.answer().modelTrusted()).as("no extension is loaded").isTrue();
        }
    }

    /** Harm prevented: what is shown reorders itself on a terminal, or shows another URL. */
    @Nested
    class WhatIsShown {
        @Test
        void aUrlKeepsItsEncodingSoNoInvisibleCharacterAppears() throws IOException {
            f.pom(
                    "pom.xml",
                    project("child", repository("r", "https://a.example.org/%E2%80%AE/a%2Fb")));

            assertThat(host(f.answer(), "a.example.org").url())
                    .as("the URL as written, not as decoded")
                    .isEqualTo("https://a.example.org/%E2%80%AE/a%2Fb");
        }

        @Test
        void anInvisibleCharacterWrittenInAUrlIsEncoded() throws IOException {
            f.pom("pom.xml", project("child", repository("r", "https://a.example.org/\u202e/x")));

            assertThat(host(f.answer(), "a.example.org").url())
                    .as("it would reorder what a terminal shows")
                    .isEqualTo("https://a.example.org/%E2%80%AE/x");
        }

        @Test
        void aFileNameCannotCarryALineBreak() throws IOException {
            f.pom("pom.xml", project("root", "  <modules><module>a&#10;b</module></modules>\n"))
                    .pom("a\nb/pom.xml", project("m", repository("r", "https://a.example.org/m")));

            assertThat(host(f.answer(), "a.example.org").place().file())
                    .as("no line of its own making in what is shown")
                    .isEqualTo("a?b/pom.xml");
        }
    }

    /** Harm prevented: a parent other than the one the build reads is merged without a word. */
    @Test
    void aParentOutsideTheCheckoutIsNamedThoughTheLocalRepositoryHoldsItsCoordinates()
            throws IOException {
        Path outside = f.checkout.resolveSibling("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("pom.xml"), project("beside", ""));
        f.localPom("org.example", "beside", "1", project("beside", ""))
                .pom("pom.xml", project("child", parent("beside", "../outside/pom.xml")));

        assertThat(f.answer().unknowns())
                .as("Maven reads the named pom, and it is not read here")
                .anyMatch(u -> u.why().contains("outside the checkout"));
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

    /** Harm prevented: a pom full of repositories holds the plugin on a CPU. */
    @Test
    void aPomWithVeryManyRepositoriesEnds() throws IOException {
        StringBuilder many = new StringBuilder("  <repositories>");
        for (int i = 0; i < 50_000; i++) {
            many.append(
                    "<repository><id>r%d</id><url>https://h.example.org/%d</url></repository>"
                            .formatted(i, i));
        }
        f.pom("pom.xml", project("child", many + "</repositories>\n"));

        RepositoryHostsAnswer answer =
                assertTimeoutPreemptively(Duration.ofSeconds(25), () -> f.answer());

        assertThat(hosts(answer)).as("and answers").contains("h.example.org");
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
                                        1)));
        private final Mirrors externalHttp =
                new Mirrors(
                        List.of(
                                new UserSettings.Mirror(
                                        "m",
                                        "https://m.example.org/",
                                        "external:http:*",
                                        "*",
                                        false,
                                        1)));

        @ParameterizedTest
        @ValueSource(strings = {" https://evil.example.org/m", "https://LOCALHOST/m"})
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
        Places places =
                new Places(
                        f.checkout,
                        f.home,
                        f.home.resolve(".m2/settings.xml"),
                        List.of(f.localRepository()));
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

        guard.read(in, Map.of(ModelProcessor.SOURCE, new FileModelSource(outside.toFile())));

        assertThat(closed).as("the builder leaves closing to its reader").isTrue();
    }

    private static final class DefaultModelBuilderFactoryProbe extends DefaultModelBuilderFactory {
        ModelProcessor processor() {
            return newModelProcessor();
        }
    }
}
