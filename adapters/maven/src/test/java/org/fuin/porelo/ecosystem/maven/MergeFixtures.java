package org.fuin.porelo.ecosystem.maven;

import static org.fuin.porelo.ecosystem.maven.Fixture.parent;
import static org.fuin.porelo.ecosystem.maven.Fixture.project;
import static org.fuin.porelo.ecosystem.maven.Fixture.properties;
import static org.fuin.porelo.ecosystem.maven.Fixture.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The merges both this library and real Maven are held to. */
final class MergeFixtures {
    private MergeFixtures() {}

    static void propertyOverriddenInTheParentUsedInTheChild(Fixture f) throws IOException {
        f.pom("grand/pom.xml", project("grand", properties("repo.host", "grand.example.org")))
                .pom(
                        "parent/pom.xml",
                        project(
                                "parent",
                                parent("grand", "../grand/pom.xml")
                                        + properties("repo.host", "parent.example.org")))
                .pom(
                        "pom.xml",
                        project(
                                "child",
                                parent("parent", "parent/pom.xml")
                                        + repository("r", "https://${repo.host}/maven")));
    }

    static void repositoryInTheGrandparentPropertyInTheChild(Fixture f) throws IOException {
        f.pom(
                        "grand/pom.xml",
                        project(
                                "grand",
                                properties("repo.host", "grand.example.org")
                                        + repository("r", "https://${repo.host}/maven")))
                .pom("parent/pom.xml", project("parent", parent("grand", "../grand/pom.xml")))
                .pom(
                        "pom.xml",
                        project(
                                "child",
                                parent("parent", "parent/pom.xml")
                                        + properties("repo.host", "child.example.org")));
    }

    static void projectFieldsAndRevision(Fixture f) throws IOException {
        f.pom(
                "pom.xml",
                project(
                        "child",
                        properties("revision", "7")
                                + repository(
                                        "r",
                                        "https://r${revision}.example.org/${project.artifactId}/${project.version}")));
    }

    static void nestedProperties(Fixture f) throws IOException {
        f.pom(
                "pom.xml",
                project(
                        "child",
                        "  <properties><a>${b}.example.org</a><b>nested</b></properties>\n"
                                + repository("r", "https://${a}/maven")));
    }

    static void propertyFromASettingsProfile(Fixture f) throws IOException {
        f.settings(
                        """
                        <settings>
                          <profiles>
                            <profile>
                              <id>corp</id>
                              <properties>
                                <repo.host>settings.example.org</repo.host>
                              </properties>
                            </profile>
                          </profiles>
                          <activeProfiles><activeProfile>corp</activeProfile></activeProfiles>
                        </settings>
                        """)
                .pom(
                        "pom.xml",
                        project(
                                "child",
                                properties("repo.host", "pom.example.org")
                                        + repository("r", "https://${repo.host}/maven")));
    }

    static void repositoryInASettingsProfile(Fixture f) throws IOException {
        f.settings(
                        """
                        <settings>
                          <profiles>
                            <profile>
                              <id>corp</id>
                              <repositories>
                                <repository>
                                  <id>corp-repo</id>
                                  <url>https://corp.example.org/maven</url>
                                </repository>
                              </repositories>
                            </profile>
                          </profiles>
                          <activeProfiles><activeProfile>corp</activeProfile></activeProfiles>
                        </settings>
                        """)
                .pom("pom.xml", project("child", ""));
    }

    static void activeByDefaultProfile(Fixture f) throws IOException {
        f.pom(
                "pom.xml",
                project(
                        "child",
                        """
                          <profiles>
                            <profile>
                              <id>on</id>
                              <activation><activeByDefault>true</activeByDefault></activation>
                              <properties><repo.host>profile.example.org</repo.host></properties>
                              <pluginRepositories>
                                <pluginRepository>
                                  <id>p</id>
                                  <url>https://${repo.host}/plugins</url>
                                </pluginRepository>
                              </pluginRepositories>
                            </profile>
                          </profiles>
                        """));
    }

    static void bomWithRepositories(Fixture f) throws IOException {
        f.localPom(
                        "org.example",
                        "bom",
                        "1",
                        project(
                                "bom",
                                properties("repo.host", "bom.example.org")
                                        + repository("bom-repo", "https://bom.example.org/maven")))
                .pom(
                        "pom.xml",
                        project(
                                "child",
                                repository("r", "https://own.example.org/maven")
                                        + """
                                          <dependencyManagement>
                                            <dependencies>
                                              <dependency>
                                                <groupId>org.example</groupId>
                                                <artifactId>bom</artifactId>
                                                <version>1</version>
                                                <type>pom</type>
                                                <scope>import</scope>
                                              </dependency>
                                            </dependencies>
                                          </dependencyManagement>
                                        """));
    }

    /** The later of two active settings profiles sets the property the repository needs. */
    static void laterSettingsProfileWins(Fixture f) throws IOException {
        f.settings(
                        """
                        <settings>
                          <profiles>
                            <profile>
                              <id>first</id>
                              <properties><repo.host>corp.example.org</repo.host></properties>
                            </profile>
                            <profile>
                              <id>second</id>
                              <properties><repo.host>${checkout.host}</repo.host></properties>
                            </profile>
                          </profiles>
                          <activeProfiles>
                            <activeProfile>first</activeProfile>
                            <activeProfile>second</activeProfile>
                          </activeProfiles>
                        </settings>
                        """)
                .pom(
                        "pom.xml",
                        project(
                                "child",
                                properties("checkout.host", "other.example.org")
                                        + repository("corp", "https://${repo.host}/maven")));
    }

    /** Real Maven applies both; a test once assumed otherwise and this comparison refuted it. */
    static void explicitSettingsProfileBesideADefaultOne(Fixture f) throws IOException {
        f.settings(
                        """
                        <settings>
                          <profiles>
                            <profile>
                              <id>by-default</id>
                              <activation><activeByDefault>true</activeByDefault></activation>
                              <repositories>
                                <repository><id>d</id><url>https://default.example.org/m</url></repository>
                              </repositories>
                            </profile>
                            <profile>
                              <id>explicit</id>
                              <repositories>
                                <repository><id>e</id><url>https://explicit.example.org/m</url></repository>
                              </repositories>
                            </profile>
                          </profiles>
                          <activeProfiles><activeProfile>explicit</activeProfile></activeProfiles>
                        </settings>
                        """)
                .pom("pom.xml", project("child", ""));
    }

    static void pomProfileNamedInTheSettings(Fixture f) throws IOException {
        f.settings(
                        """
                        <settings><activeProfiles>
                          <activeProfile>a</activeProfile>
                          <activeProfile>b</activeProfile>
                        </activeProfiles></settings>
                        """)
                .pom(
                        "pom.xml",
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
                                        + repository("r", "https://${repo.host}/maven")));
    }

    static void defaultSettingsProfileSharingAPomProfilesId(Fixture f) throws IOException {
        f.settings(
                        """
                        <settings><profiles><profile>
                          <id>corp</id>
                          <activation><activeByDefault>true</activeByDefault></activation>
                          <repositories>
                            <repository><id>s</id><url>https://settings.example.org/m</url></repository>
                          </repositories>
                        </profile></profiles></settings>
                        """)
                .pom(
                        "pom.xml",
                        project(
                                "child",
                                """
                                  <profiles>
                                    <profile>
                                      <id>corp</id>
                                      <repositories>
                                        <repository><id>c</id><url>https://pomcorp.example.org/m</url></repository>
                                      </repositories>
                                    </profile>
                                    <profile>
                                      <id>other</id>
                                      <activation><activeByDefault>true</activeByDefault></activation>
                                      <repositories>
                                        <repository><id>o</id><url>https://other.example.org/m</url></repository>
                                      </repositories>
                                    </profile>
                                  </profiles>
                                """));
    }

    private static final String REPO_FROM_A_PROPERTY =
            """
              <properties><repo.host>pom.example.org</repo.host></properties>
              <repositories>
                <repository><id>r</id><url>https://${repo.host}/m</url></repository>
              </repositories>
            """;

    static void propertyInMavenConfig(Fixture f) throws IOException {
        f.pom(".mvn/maven.config", "-Drepo.host=config.example.org\n")
                .pom("pom.xml", project("child", REPO_FROM_A_PROPERTY));
    }

    static void globalSettingsWithAProfileAndAMirror(Fixture f) throws IOException {
        f.pom(".mvn/maven.config", "-gs\nci/global.xml\n")
                .pom(
                        "ci/global.xml",
                        """
                        <settings><profiles><profile>
                          <id>g</id>
                          <activation><activeByDefault>true</activeByDefault></activation>
                          <repositories><repository>
                            <id>from-global</id><url>https://global.example.org/m</url>
                          </repository></repositories>
                        </profile></profiles></settings>
                        """)
                .pom("pom.xml", project("child", ""));
    }

    static void anActiveIdNamingAGlobalProfile(Fixture f) throws IOException {
        f.pom(".mvn/maven.config", "-gs\nci/global.xml\n")
                .pom(
                        "ci/global.xml",
                        """
                        <settings><profiles><profile>
                          <id>g</id>
                          <repositories><repository>
                            <id>from-global</id><url>https://global.example.org/m</url>
                          </repository></repositories>
                        </profile></profiles></settings>
                        """)
                .settings(
                        "<settings><activeProfiles><activeProfile>g</activeProfile></activeProfiles></settings>")
                .pom("pom.xml", project("child", ""));
    }

    static void theUsersProfileOverAGlobalOneOfItsId(Fixture f) throws IOException {
        String profile =
                """
                <settings><profiles><profile>
                  <id>g</id>
                  <activation><activeByDefault>true</activeByDefault></activation>
                  <repositories><repository>
                    <id>%s</id><url>https://%s.example.org/m</url>
                  </repository></repositories>
                </profile></profiles></settings>
                """;
        f.pom(".mvn/maven.config", "-gs\nci/global.xml\n")
                .pom("ci/global.xml", profile.formatted("global", "global"))
                .settings(profile.formatted("user", "user"))
                .pom("pom.xml", project("child", ""));
    }

    /** Asks for the repository under the name the task's settings declare it. */
    static void aRepositoryOfTheFileConfigureWrites(Fixture f) throws IOException {
        f.settings(
                        MavenSettingsFile.of(
                                        java.util.List.of(
                                                new org.fuin.porelo.ecosystem.api.Repository(
                                                        "corp", "http://127.0.0.1:9420/corp/")))
                                .get(0)
                                .content())
                .pom(
                        "pom.xml",
                        project("child", repository("corp", "https://nexus.example.org/m")));
    }

    static void twoPropertiesOnOneLineOfMavenConfig(Fixture f) throws IOException {
        f.pom(".mvn/maven.config", "-Dz=1 -Drepo.host=config.example.org\n")
                .pom("pom.xml", project("child", REPO_FROM_A_PROPERTY));
    }

    static void propertyOnTwoLinesOfMavenConfig(Fixture f) throws IOException {
        f.pom(".mvn/maven.config", "-D\nrepo.host=config.example.org\n")
                .pom("pom.xml", project("child", REPO_FROM_A_PROPERTY));
    }

    static void propertyInJvmConfigBesideOneInThePom(Fixture f) throws IOException {
        f.pom(".mvn/jvm.config", "-Xmx1g -Drepo.host=jvm.example.org\n")
                .pom("pom.xml", project("child", REPO_FROM_A_PROPERTY));
    }

    static void propertyInJvmConfigAlone(Fixture f) throws IOException {
        f.pom(".mvn/jvm.config", "-Drepo.host=jvm.example.org\n")
                .pom("pom.xml", project("child", repository("r", "https://${repo.host}/m")));
    }

    private static final String SETTINGS_REPOSITORY_FROM_A_PROPERTY =
            """
            <settings><profiles><profile><id>p</id>
              <activation><activeByDefault>true</activeByDefault></activation>
              <properties><repo.host>settings.example.org</repo.host></properties>
              <repositories>
                <repository><id>s</id><url>https://${repo.host}/m</url></repository>
              </repositories>
            </profile></profiles></settings>
            """;

    static void mavenConfigFillsInTheSettings(Fixture f) throws IOException {
        f.settings(SETTINGS_REPOSITORY_FROM_A_PROPERTY)
                .pom(".mvn/maven.config", "-Drepo.host=config.example.org\n")
                .pom("pom.xml", project("child", ""));
    }

    static void jvmConfigFillsInTheSettings(Fixture f) throws IOException {
        f.settings(SETTINGS_REPOSITORY_FROM_A_PROPERTY)
                .pom(".mvn/jvm.config", "-Drepo.host=jvm.example.org\n")
                .pom("pom.xml", project("child", ""));
    }

    private static final String TWO_PROFILES =
            """
              <profiles>
                <profile>
                  <id>named</id>
                  <repositories>
                    <repository><id>n</id><url>https://named.example.org/m</url></repository>
                  </repositories>
                </profile>
                <profile>
                  <id>usual</id>
                  <activation><activeByDefault>true</activeByDefault></activation>
                  <repositories>
                    <repository><id>u</id><url>https://usual.example.org/m</url></repository>
                  </repositories>
                </profile>
              </profiles>
            """;

    static void profileInMavenConfig(Fixture f) throws IOException {
        f.pom(".mvn/maven.config", "-Pnamed\n").pom("pom.xml", project("child", TWO_PROFILES));
    }

    static void profileTurnedOffInMavenConfig(Fixture f) throws IOException {
        f.pom(".mvn/maven.config", "-P!usual\n").pom("pom.xml", project("child", TWO_PROFILES));
    }

    private static final String PROFILES_ON_A_PROPERTY_AND_A_FILE =
            """
              <profiles>
                <profile>
                  <id>on-property</id>
                  <activation><property><name>turn.on</name></property></activation>
                  <repositories>
                    <repository><id>p</id><url>https://property.example.org/m</url></repository>
                  </repositories>
                </profile>
                <profile>
                  <id>off-property</id>
                  <activation><property><name>!turn.on</name></property></activation>
                  <repositories>
                    <repository><id>q</id><url>https://not-property.example.org/m</url></repository>
                  </repositories>
                </profile>
                <profile>
                  <id>on-file</id>
                  <activation><file><exists>marker.txt</exists></file></activation>
                  <repositories>
                    <repository><id>f</id><url>https://file.example.org/m</url></repository>
                  </repositories>
                </profile>
              </profiles>
            """;

    static void profilesOnNothingGiven(Fixture f) throws IOException {
        f.pom("pom.xml", project("child", PROFILES_ON_A_PROPERTY_AND_A_FILE));
    }

    static void profilesOnAPropertyInMavenConfigAndAFile(Fixture f) throws IOException {
        f.pom(".mvn/maven.config", "-Dturn.on=yes\n")
                .pom("marker.txt", "")
                .pom("pom.xml", project("child", PROFILES_ON_A_PROPERTY_AND_A_FILE));
    }

    static void parentBesideTheCheckout(Fixture f) throws IOException {
        Path outside = f.checkout.resolveSibling("beside");
        Files.createDirectories(outside);
        Files.writeString(
                outside.resolve("pom.xml"),
                project("beside", repository("b", "https://beside.example.org/m")));
        f.pom("pom.xml", project("child", parent("beside", "../beside/pom.xml")));
    }

    static void leadingDotInAnExpression(Fixture f) throws IOException {
        f.pom(
                "pom.xml",
                project(
                        "child",
                        properties("repo.host", "dot.example.org")
                                + repository("r", "https://${.repo.host}/maven")));
    }

    static void projectFieldOverAPropertyOfItsName(Fixture f) throws IOException {
        f.pom(
                "pom.xml",
                project(
                        "child",
                        "  <name>field.example.org</name>\n"
                                + properties("project.name", "property.example.org")
                                + repository("r", "https://${project.name}/maven")));
    }

    /** A profile real Maven applies on any machine with a JDK, and this library names unknown. */
    static void profileOnAJdk(Fixture f) throws IOException {
        f.pom(
                "pom.xml",
                project(
                        "child",
                        """
                          <profiles>
                            <profile>
                              <id>on-jdk</id>
                              <activation><jdk>[1,)</jdk></activation>
                              <repositories>
                                <repository>
                                  <id>jdk-repo</id>
                                  <url>https://jdk.example.org/maven</url>
                                </repository>
                              </repositories>
                            </profile>
                          </profiles>
                        """));
    }
}
