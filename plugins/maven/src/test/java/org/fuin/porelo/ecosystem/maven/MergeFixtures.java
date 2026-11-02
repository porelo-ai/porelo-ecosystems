package org.fuin.porelo.ecosystem.maven;

import static org.fuin.porelo.ecosystem.maven.Fixture.parent;
import static org.fuin.porelo.ecosystem.maven.Fixture.project;
import static org.fuin.porelo.ecosystem.maven.Fixture.properties;
import static org.fuin.porelo.ecosystem.maven.Fixture.repository;

import java.io.IOException;

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
                                properties("checkout.host", "evil.example.org")
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
