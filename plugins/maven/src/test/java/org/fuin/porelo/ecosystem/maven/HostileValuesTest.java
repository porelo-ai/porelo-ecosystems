package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;
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
                    .as("named, not dropped")
                    .anyMatch(u -> u.why().contains("corp-repo"));
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

            assertThat(f.answer().hosts())
                    .as(
                            "its coordinates stand in the checkout, and the host must not be the person's")
                    .noneMatch(
                            h ->
                                    h.host().equals("evil.example.org")
                                            && h.origin() != Origin.CHECKOUT);
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

    /** Harm prevented: a repository the person's mirror covers is answered as reached directly. */
    @Nested
    class ARepositorysUrlAsMavenTakesItApart {
        private final Mirrors external =
                new Mirrors(
                        List.of(
                                new UserSettings.Mirror(
                                        "m", "https://m.example.org/", "external:*", "*", 1)));
        private final Mirrors externalHttp =
                new Mirrors(
                        List.of(
                                new UserSettings.Mirror(
                                        "m", "https://m.example.org/", "external:http:*", "*", 1)));

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
