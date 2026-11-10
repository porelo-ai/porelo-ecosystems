package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A checkout and a home, written as the real files would be. */
final class Fixture {
    final Path checkout;
    final Path home;

    Fixture(Path root) throws IOException {
        checkout = Files.createDirectories(root.resolve("checkout"));
        home = Files.createDirectories(root.resolve("home"));
    }

    Fixture pom(String relative, String xml) throws IOException {
        return write(checkout.resolve(relative), xml);
    }

    Fixture settings(String xml) throws IOException {
        return write(home.resolve(".m2/settings.xml"), xml);
    }

    Path localRepository() {
        return home.resolve(".m2/repository");
    }

    Fixture localPom(String group, String artifact, String version, String xml) throws IOException {
        return write(
                localRepository()
                        .resolve(group.replace('.', '/'))
                        .resolve(artifact)
                        .resolve(version)
                        .resolve(artifact + "-" + version + ".pom"),
                xml);
    }

    RepositoryHostsAnswer answer() throws IOException {
        return RepositoryHosts.read(checkout, home);
    }

    private Fixture write(Path file, String xml) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, xml);
        return this;
    }

    /** A pom's head, with what follows it inside the project element. */
    static String project(String artifact, String body) {
        return """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>%s</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                %s
                </project>
                """
                .formatted(artifact, body);
    }

    static String parent(String artifact, String relativePath) {
        return """
                  <parent>
                    <groupId>org.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>1</version>
                    <relativePath>%s</relativePath>
                  </parent>
                """
                .formatted(artifact, relativePath);
    }

    static String repository(String id, String url) {
        return """
                  <repositories>
                    <repository>
                      <id>%s</id>
                      <url>%s</url>
                    </repository>
                  </repositories>
                """
                .formatted(id, url);
    }

    static String properties(String name, String value) {
        return "  <properties><%s>%s</%s></properties>\n".formatted(name, value, name);
    }
}
