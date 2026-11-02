package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.fuin.porelo.ecosystem.maven.Fixture.parent;
import static org.fuin.porelo.ecosystem.maven.Fixture.project;
import static org.fuin.porelo.ecosystem.maven.Fixture.properties;
import static org.fuin.porelo.ecosystem.maven.Fixture.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RepositoryHostsTest {
    @TempDir Path root;
    Fixture f;

    @BeforeEach
    void fixture() throws IOException {
        f = new Fixture(root);
    }

    private static RepositoryHost host(RepositoryHostsAnswer answer, String name) {
        return answer.hosts().stream()
                .filter(h -> h.host().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " not in " + answer));
    }

    private static List<String> hosts(RepositoryHostsAnswer answer) {
        return answer.hosts().stream().map(RepositoryHost::host).distinct().toList();
    }

    /** Harm prevented: a value's file and origin decide whether the person trusts a host. */
    @Nested
    class MergedValues {
        @Test
        void aValueNamesWhereItsRepositoryAndItsPropertyStand() throws IOException {
            MergeFixtures.propertyOverriddenInTheParentUsedInTheChild(f);

            RepositoryHost host = host(f.answer(), "parent.example.org");

            assertThat(host.place().file())
                    .as("the repository stands in the child")
                    .isEqualTo("pom.xml");
            assertThat(host.place().line()).as("the url's line").isPositive();
            assertThat(host.properties())
                    .as("the property that won stands in the parent")
                    .extracting(u -> u.name() + " " + u.place().file())
                    .containsExactly("repo.host parent/pom.xml");
            assertThat(host.properties().get(0).place().line())
                    .as("the property's line")
                    .isPositive();
        }

        @Test
        void aRepositoryInALocalParentFilledInFromTheCheckoutIsTheCheckouts() throws IOException {
            f.localPom(
                            "org.example",
                            "grand",
                            "1",
                            project("grand", repository("r", "https://${repo.host}/maven")))
                    .pom(
                            "pom.xml",
                            project(
                                    "child",
                                    parent("grand", "")
                                            + properties("repo.host", "evil.example.org")));

            RepositoryHost host = host(f.answer(), "evil.example.org");

            assertThat(host.place().origin())
                    .as("the repository stands in the parent")
                    .isEqualTo(Origin.MACHINE);
            assertThat(host.origin())
                    .as("but the checkout chose its host")
                    .isEqualTo(Origin.CHECKOUT);
        }

        @Test
        void aSettingsRepositoryFilledInFromTheCheckoutIsTheCheckouts() throws IOException {
            f.settings(
                            """
                            <settings>
                              <profiles>
                                <profile>
                                  <id>corp</id>
                                  <repositories>
                                    <repository>
                                      <id>corp-repo</id>
                                      <url>https://${repo.host}/maven</url>
                                    </repository>
                                  </repositories>
                                </profile>
                              </profiles>
                              <activeProfiles><activeProfile>corp</activeProfile></activeProfiles>
                            </settings>
                            """)
                    .pom("pom.xml", project("child", properties("repo.host", "evil.example.org")));

            RepositoryHost host = host(f.answer(), "evil.example.org");

            assertThat(host.place().origin())
                    .as("the repository stands in the settings")
                    .isEqualTo(Origin.YOURS);
            assertThat(host.place().file())
                    .as("named relative to the home")
                    .isEqualTo(".m2/settings.xml");
            assertThat(host.origin())
                    .as("a forge must not get a host marked as the person's")
                    .isEqualTo(Origin.CHECKOUT);
        }

        @Test
        void aSettingsPropertyWinsAndIsNamedAsThePersons() throws IOException {
            MergeFixtures.propertyFromASettingsProfile(f);

            RepositoryHost host = host(f.answer(), "settings.example.org");

            assertThat(host.properties())
                    .as("the settings' value won over the pom's")
                    .extracting(u -> u.place().origin())
                    .containsExactly(Origin.YOURS);
            assertThat(host.properties().get(0).place().line())
                    .as("the property's line")
                    .isPositive();
        }

        @Test
        void aNestedPropertyNamesEveryLevelItCameThrough() throws IOException {
            MergeFixtures.nestedProperties(f);

            assertThat(host(f.answer(), "nested.example.org").properties())
                    .extracting(PropertyUse::name)
                    .as("both the property and the one inside it")
                    .containsExactly("a", "b");
        }

        @Test
        void eachModuleFillsInASettingsRepositoryFromItsOwnProperty() throws IOException {
            f.settings(
                            """
                            <settings>
                              <profiles>
                                <profile>
                                  <id>corp</id>
                                  <repositories>
                                    <repository>
                                      <id>corp-repo</id>
                                      <url>https://${repo.host}/maven</url>
                                    </repository>
                                  </repositories>
                                </profile>
                              </profiles>
                              <activeProfiles><activeProfile>corp</activeProfile></activeProfiles>
                            </settings>
                            """)
                    .pom(
                            "pom.xml",
                            project(
                                    "root",
                                    properties("repo.host", "one.example.org")
                                            + "  <modules><module>sub</module></modules>"))
                    .pom("sub/pom.xml", project("sub", properties("repo.host", "two.example.org")));

            assertThat(hosts(f.answer()))
                    .as("the first pom's filling-in must not stay for the next")
                    .contains("one.example.org", "two.example.org");
        }

        @Test
        void aModulesRepositoryIsAnswered() throws IOException {
            f.pom("pom.xml", project("root", "  <modules><module>sub</module></modules>"))
                    .pom(
                            "sub/pom.xml",
                            project("sub", repository("r", "https://sub.example.org/m")));

            assertThat(host(f.answer(), "sub.example.org").place().file())
                    .as("a build must reach what any module names")
                    .isEqualTo("sub/pom.xml");
        }

        @Test
        void builtInCentralIsTheMachinesWithNoFile() throws IOException {
            f.pom("pom.xml", project("root", ""));

            Place place = host(f.answer(), "repo.maven.apache.org").place();

            assertThat(place)
                    .as("the super POM is no file")
                    .isEqualTo(new Place("", 0, Origin.MACHINE));
        }
    }

    /**
     * Harm prevented: a pom puts a secret of the machine into a URL, and so into the draft shown.
     */
    @Nested
    class NothingOfTheMachine {
        @Test
        void aPropertyNothingDefinesIsUnknownAndNoHost() throws IOException {
            f.pom("pom.xml", project("child", repository("r", "https://${nowhere}.example.org/m")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("no host from half a URL")
                    .containsExactly("repo.maven.apache.org");
            assertThat(answer.unknowns())
                    .as("named, with where it is asked for")
                    .anyMatch(
                            u ->
                                    u.what().equals("${nowhere}")
                                            && u.place().file().equals("pom.xml"));
        }

        @Test
        void anEnvironmentVariableIsNeverFilledIn() throws IOException {
            String secret = System.getenv("PATH");
            assertThat(secret).as("the guard needs a variable that is set").isNotBlank();
            f.pom(
                    "pom.xml",
                    project("child", repository("r", "https://h.example.org/${env.PATH}")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.toString()).as("its value appears nowhere").doesNotContain(secret);
            assertThat(answer.unknowns())
                    .as("the variable is named")
                    .anyMatch(
                            u -> u.what().equals("${env.PATH}") && u.why().contains("environment"));
        }

        @Test
        void aSystemPropertyIsNeverFilledIn() throws IOException {
            String secret = System.getProperty("java.home");
            f.pom(
                    "pom.xml",
                    project("child", repository("r", "https://h.example.org/${java.home}")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.toString()).as("its value appears nowhere").doesNotContain(secret);
            assertThat(answer.unknowns())
                    .as("the property is named")
                    .anyMatch(u -> u.what().equals("${java.home}"));
        }

        @Test
        void theBasedirInAHostIsNotAnsweredAsAHost() throws IOException {
            f.pom(
                    "pom.xml",
                    project("child", repository("r", "https://${project.basedir}.example.org/")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("a path is no host")
                    .containsExactly("repo.maven.apache.org");
            assertThat(answer.toString()).as("nor shown").doesNotContain(root.toString());
        }

        @Test
        void aProfileOnAFileOrPropertyIsUnknownAndItsPropertiesAreNotApplied() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            """
                              <profiles>
                                <profile>
                                  <id>on-absent</id>
                                  <activation><property><name>!never.set</name></property></activation>
                                  <properties><repo.host>conditional.example.org</repo.host></properties>
                                </profile>
                              </profiles>
                            """
                                    + repository("r", "https://${repo.host}/m")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("no host from the profile")
                    .doesNotContain("conditional.example.org");
            assertThat(answer.unknowns())
                    .extracting(UnknownValue::what)
                    .as("both named")
                    .contains("profile on-absent", "${repo.host}");
        }
    }

    /** Harm prevented: a credential leaves the person's settings; a mirror is bypassed unseen. */
    @Nested
    class TheSettings {
        private static final String WITH_PASSWORD =
                """
                <settings>
                  <mirrors>
                    <mirror>
                      <id>corp-nexus</id>
                      <mirrorOf>*</mirrorOf>
                      <url>https://nexus.corp.example.org/all</url>
                    </mirror>
                  </mirrors>
                  <servers>
                    <server>
                      <id>corp-nexus</id>
                      <username>someone</username>
                      <password>the-password-value</password>
                      <passphrase>the-passphrase-value</passphrase>
                      <privateKey>the-key-path</privateKey>
                    </server>
                  </servers>
                </settings>
                """;

        @Test
        void aMirrorOfEverythingLeavesCentralAndTheCheckoutsHostOut() throws IOException {
            f.settings(WITH_PASSWORD)
                    .pom(
                            "pom.xml",
                            project("child", repository("r", "https://repo.example.org/m")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("only the mirror is reached")
                    .containsExactly("nexus.corp.example.org");
            RepositoryHost mirror = answer.hosts().get(0);
            assertThat(mirror.origin()).as("the person's").isEqualTo(Origin.YOURS);
            assertThat(mirror.kind()).as("a mirror").isEqualTo(RepositoryHost.Kind.MIRROR);
            assertThat(mirror.place().file()).as("with its file").isEqualTo(".m2/settings.xml");
        }

        @Test
        void aServerPasswordAppearsNowhereAndItsIdIsKnown() throws IOException {
            f.settings(WITH_PASSWORD).pom("pom.xml", project("child", ""));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.toString())
                    .as("no value of the server appears")
                    .doesNotContain(
                            "the-password-value",
                            "the-passphrase-value",
                            "the-key-path",
                            "someone");
            assertThat(answer.hosts().get(0).server())
                    .as("only its id, and that it is known")
                    .extracting(ServerReference::id, ServerReference::known)
                    .containsExactly("corp-nexus", true);
        }

        @Test
        void aRepositoryWithNoServerIsAnsweredAsNotKnown() throws IOException {
            f.pom("pom.xml", project("child", repository("r", "https://a.example.org/m")));

            assertThat(host(f.answer(), "a.example.org").server().known())
                    .as("no settings name it")
                    .isFalse();
        }

        @Test
        void aUrlsUserInfoAndQueryAppearNowhere() throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            repository(
                                    "r",
                                    "https://user:url-secret@a.example.org/m?token=query-secret#frag")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.toString())
                    .as("no secret of the URL")
                    .doesNotContain("url-secret", "query-secret", "user:");
            assertThat(host(answer, "a.example.org").url())
                    .as("the URL without them")
                    .isEqualTo("https://a.example.org/m");
        }

        @ParameterizedTest(name = "mirrorOf {0} on {1}: {2}")
        @CsvSource({
            "'*', r, true",
            "'r', r, true",
            "'other', r, false",
            "'*,!r', r, false",
            "'!r,*', r, false",
            "'a,r', r, true",
            "'external:*', r, true",
            "'external:http:*', r, false",
            "'*,external:http:*', r, true",
            "'external:*,external:http:*', r, true",
            "'*, !r', r, true",
            "'r , a', r, false",
        })
        void aMirrorIsSelectedAsMavenSelectsIt(String pattern, String id, boolean mirrored)
                throws IOException {
            f.settings(
                            """
                            <settings><mirrors><mirror>
                              <id>m</id><mirrorOf>%s</mirrorOf><url>https://mirror.example.org/</url>
                            </mirror></mirrors></settings>
                            """
                                    .formatted(pattern))
                    .pom("pom.xml", project("child", repository(id, "https://r.example.org/m")));

            assertThat(hosts(f.answer()).contains("r.example.org"))
                    .as("whether the repository stays reachable under %s", pattern)
                    .isEqualTo(!mirrored);
        }

        @Test
        void anExactIdWinsOverAnEarlierPattern() throws IOException {
            f.settings(
                            """
                            <settings><mirrors>
                              <mirror><id>all</id><mirrorOf>*</mirrorOf><url>https://all.example.org/</url></mirror>
                              <mirror><id>own</id><mirrorOf>r</mirrorOf><url>https://own.example.org/</url></mirror>
                            </mirrors></settings>
                            """)
                    .pom("pom.xml", project("child", repository("r", "https://r.example.org/m")));

            assertThat(hosts(f.answer()))
                    .as("r takes its own mirror, central the other")
                    .contains("own.example.org", "all.example.org");
        }

        @Test
        void aSettingsFileWithADtdIsRefused() throws IOException {
            f.settings("<!DOCTYPE settings [<!ENTITY x \"y\">]><settings/>")
                    .pom("pom.xml", project("child", ""));

            assertThatThrownBy(f::answer)
                    .as("the person's own file is hardened too, and fails loud")
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("DTD");
        }
    }

    /** Harm prevented: a pom a forge wrote reads another file or exhausts the machine. */
    @Nested
    class HostilePoms {
        @Test
        void anExternalEntityYieldsAnAnswerWithoutTheFilesContent() throws IOException {
            Path secret = Files.writeString(root.resolve("secret.txt"), "local-file-content");
            f.pom(
                    "pom.xml",
                    """
                    <?xml version="1.0"?>
                    <!DOCTYPE project [<!ENTITY x SYSTEM "%s">]>
                    <project><modelVersion>4.0.0</modelVersion><groupId>g</groupId><artifactId>a</artifactId><version>1</version>
                      <repositories><repository><id>r</id><url>https://a.example.org/&x;</url></repository></repositories>
                    </project>
                    """
                            .formatted(secret.toUri()));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.toString())
                    .as("the file's content appears nowhere")
                    .doesNotContain("local-file-content");
            assertThat(answer.unknowns())
                    .as("the pom is refused by name")
                    .anyMatch(u -> u.what().equals("pom.xml") && u.why().contains("DTD"));
        }

        @Test
        void nestedEntityExpansionIsRefusedWithItsName() throws IOException {
            StringBuilder entities = new StringBuilder("<!ENTITY a \"aaaaaaaaaa\">");
            String previous = "a";
            for (String name : List.of("b", "c", "d", "e", "f", "g", "h", "i", "j")) {
                String ref = "&" + previous + ";";
                entities.append("<!ENTITY ")
                        .append(name)
                        .append(" \"")
                        .append(ref.repeat(10))
                        .append("\">");
                previous = name;
            }
            f.pom(
                            "sub/pom.xml",
                            "<!DOCTYPE project ["
                                    + entities
                                    + "]><project><name>&j;</name></project>")
                    .pom("pom.xml", project("root", "  <modules><module>sub</module></modules>"));

            assertThat(f.answer().unknowns())
                    .as("named, and the rest of the checkout still answered")
                    .anyMatch(u -> u.what().equals("sub/pom.xml") && u.why().contains("DTD"));
        }

        @Test
        void aPomOverTheLimitIsRefusedWithItsName() throws IOException {
            f.pom("pom.xml", project("big", "<!--" + "x".repeat(PomGuard.MAX_POM_BYTES) + "-->"));

            assertThat(f.answer().unknowns())
                    .as("refused by size")
                    .anyMatch(u -> u.what().equals("pom.xml") && u.why().contains("larger"));
        }

        @Test
        @Timeout(120)
        void propertiesThatDoubleAtEachLevelAreRefusedAcrossPoms() throws IOException {
            StringBuilder below = new StringBuilder();
            StringBuilder above = new StringBuilder("<p0>xxxxxxxxxxxxxxxx</p0>");
            for (int i = 1; i <= 40; i++) {
                StringBuilder level = i % 2 == 0 ? below : above;
                level.append("<p%d>${p%d}${p%d}</p%d>".formatted(i, i - 1, i - 1, i));
            }
            f.localPom(
                            "org.example",
                            "grand",
                            "1",
                            project("grand", "  <properties>" + above + "</properties>\n"))
                    .pom(
                            "pom.xml",
                            project(
                                    "bomb",
                                    parent("grand", "")
                                            + "  <properties>"
                                            + below
                                            + "</properties>\n"
                                            + repository("r", "https://${p40}.example.org/")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer)).as("no host from it").containsExactly();
            assertThat(answer.unknowns())
                    .as("refused by name before anything is filled in")
                    .anyMatch(u -> u.why().contains("would grow"));
        }

        @Test
        void coordinatesCannotLeaveTheLocalRepository() throws IOException {
            Path target = f.localRepository().getParent().resolve("outside/outside/x-1.pom");
            Files.createDirectories(target.getParent());
            Files.writeString(
                    target, project("x", repository("o", "https://outside.example.org/")));
            Files.createDirectories(f.localRepository());
            f.pom(
                    "pom.xml",
                    """
                    <project><modelVersion>4.0.0</modelVersion>
                      <parent><groupId>g</groupId><artifactId>../../outside/x</artifactId><version>1</version><relativePath/></parent>
                      <artifactId>a</artifactId>
                    </project>
                    """);

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("nothing read from outside")
                    .doesNotContain("outside.example.org");
            assertThat(answer.unknowns())
                    .as("the coordinates are refused before they form a path")
                    .anyMatch(
                            u ->
                                    u.what().equals("g:../../outside/x:1")
                                            && u.why().contains("cannot be looked up"));
        }

        @Test
        void aParentVersionRangeIsNamedAndNotGuessed() throws IOException {
            f.pom(
                    "pom.xml",
                    """
                    <project><modelVersion>4.0.0</modelVersion>
                      <parent><groupId>g</groupId><artifactId>p</artifactId><version>[1,2)</version><relativePath/></parent>
                      <artifactId>a</artifactId>
                      <version>1</version>
                    </project>
                    """);

            assertThat(f.answer().toString()).as("named, not resolved").contains("g:p:[1,2)");
        }

        @Test
        void aRelativePathOutsideTheCheckoutIsNotRead() throws IOException {
            Files.writeString(
                    root.resolve("outside.xml"),
                    project("outside", repository("o", "https://outside.example.org/")));
            f.pom("pom.xml", project("child", parent("outside", "../outside.xml")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer))
                    .as("nothing read from outside")
                    .doesNotContain("outside.example.org");
            assertThat(answer.unknowns())
                    .as("the parent is named unknown")
                    .anyMatch(u -> u.what().contains("org.example:outside:1"));
        }

        @Test
        void aLinkInTheCheckoutCannotReachOutside() throws IOException {
            Path outside =
                    Files.writeString(
                            root.resolve("outside.xml"),
                            project("outside", repository("o", "https://outside.example.org/")));
            f.pom("pom.xml", project("child", parent("outside", "link.xml")));
            Files.createSymbolicLink(f.checkout.resolve("link.xml"), outside);

            assertThat(hosts(f.answer()))
                    .as("the link's target is not read")
                    .doesNotContain("outside.example.org");
        }

        @Test
        void aModuleOutsideTheCheckoutIsNamedAndNotRead() throws IOException {
            Files.createDirectories(root.resolve("out"));
            Files.writeString(
                    root.resolve("out/pom.xml"),
                    project("out", repository("o", "https://outside.example.org/")));
            f.pom("pom.xml", project("root", "  <modules><module>../out</module></modules>"));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(hosts(answer)).as("not read").doesNotContain("outside.example.org");
            assertThat(answer.unknowns())
                    .as("named")
                    .anyMatch(u -> u.what().equals("module ../out"));
        }

        @Test
        void aControlCharacterInAnIdNeverReachesTheAnswer() throws IOException {
            f.pom(
                    "pom.xml",
                    project("child", repository("evil&#x9B;2J&#x202E;", "https://a.example.org/")));

            assertThat(host(f.answer(), "a.example.org").id())
                    .as("no terminal escape, and no reordering of what is shown")
                    .isEqualTo("evil?2J?");
        }
    }

    /**
     * Harm prevented: the extension's code would run on the host; a missing parent is guessed at.
     */
    @Nested
    class WhatIsNotThere {
        @Test
        void aCheckoutWithExtensionsIsNamedAndNotTrusted() throws IOException {
            f.pom("pom.xml", project("child", ""));
            f.pom(".mvn/extensions.xml", "<extensions/>");

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.modelTrusted()).as("its model could be changed").isFalse();
            assertThat(answer.unplaced())
                    .as("the file is named")
                    .anyMatch(u -> u.place().file().equals(".mvn/extensions.xml"));
        }

        @Test
        void aCheckoutWithoutExtensionsIsTrusted() throws IOException {
            f.pom("pom.xml", project("child", ""));

            assertThat(f.answer().modelTrusted()).as("nothing could change its model").isTrue();
        }

        @Test
        void aMissingParentIsNamedWithItsCoordinatesFileAndLineAndTheRestAnswers()
                throws IOException {
            f.pom(
                    "pom.xml",
                    project(
                            "child",
                            parent("gone", "") + repository("r", "https://own.example.org/m")));

            RepositoryHostsAnswer answer = f.answer();

            assertThat(answer.unknowns())
                    .as("named, asked for by the child")
                    .anyMatch(
                            u ->
                                    u.what().equals("org.example:gone:1")
                                            && u.place().file().equals("pom.xml")
                                            && u.place().line() > 0);
            assertThat(hosts(answer))
                    .as("what the child says itself still answers")
                    .contains("own.example.org");
        }

        @Test
        void aParentPresentLocallyIsAnsweredAsTheMachines() throws IOException {
            f.localPom(
                            "org.example",
                            "here",
                            "1",
                            project("here", repository("r", "https://parent.example.org/m")))
                    .pom("pom.xml", project("child", parent("here", "")));

            RepositoryHost host = host(f.answer(), "parent.example.org");

            assertThat(host.origin())
                    .as("neither the person nor the checkout wrote it")
                    .isEqualTo(Origin.MACHINE);
            assertThat(host.place().file())
                    .as("relative to the home")
                    .startsWith(".m2/repository/org/example/here");
        }

        @Test
        void aParentInASplitRepositorysPartIsFound() throws IOException {
            Path part = f.localRepository().resolve("cached/org/example/here/1/here-1.pom");
            Files.createDirectories(part.getParent());
            Files.writeString(
                    part, project("here", repository("r", "https://split.example.org/m")));
            f.pom("pom.xml", project("child", parent("here", "")));

            assertThat(hosts(f.answer()))
                    .as("found in the part for downloads")
                    .contains("split.example.org");
        }

        @Test
        void aMissingBomAnswersExactlyAsAPresentOne() throws IOException {
            MergeFixtures.bomWithRepositories(f);
            RepositoryHostsAnswer present = f.answer();
            Files.delete(f.localRepository().resolve("org/example/bom/1/bom-1.pom"));

            RepositoryHostsAnswer missing = f.answer();

            assertThat(missing).as("an import brings no host and no property").isEqualTo(present);
            assertThat(hosts(missing))
                    .as("and the BOM's own repository is not answered")
                    .doesNotContain("bom.example.org");
            assertThat(missing.unknowns()).as("nothing is unknown for it").isEmpty();
        }

        @Test
        void aDirectoryWithoutAPomIsNoMavenCheckout() {
            assertThatThrownBy(f::answer)
                    .as("said, and no empty answer")
                    .isInstanceOf(NotAMavenCheckoutException.class);
        }
    }

    @Test
    void readingLeavesTheLocalRepositoryAndTheCheckoutUnchanged() throws IOException {
        f.localPom("org.example", "here", "1", project("here", ""))
                .pom("pom.xml", project("child", parent("here", "")));
        List<String> before = listing();

        f.answer();

        assertThat(listing()).as("nothing is written anywhere, not even a lock").isEqualTo(before);
    }

    private List<String> listing() throws IOException {
        try (var files = Files.walk(root)) {
            return files.map(Path::toString).sorted().toList();
        }
    }
}
