package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.fuin.porelo.ecosystem.maven.Fixture.project;
import static org.fuin.porelo.ecosystem.maven.Fixture.properties;
import static org.fuin.porelo.ecosystem.maven.Fixture.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.fuin.porelo.ecosystem.api.Detection;
import org.fuin.porelo.ecosystem.api.File;
import org.fuin.porelo.ecosystem.api.InstalledEcosystem;
import org.fuin.porelo.ecosystem.api.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs only with the executable built: what a JVM finds by itself, an image must be told of. */
class NativeExecutableIT {
    @TempDir Path root;

    // Under the name it is installed by: Porelo refuses one that calls itself anything else.
    private Path executable() throws IOException {
        Path bin = Files.createDirectories(root.resolve("bin"));
        return Files.copy(
                Path.of(System.getProperty("test.native.executable")), bin.resolve("maven"));
    }

    private Fixture checkout() throws IOException {
        Fixture f = new Fixture(root);
        f.pom(
                        "pom.xml",
                        project(
                                "child",
                                properties("maven.compiler.release", "25")
                                        // Each walks the model by reflection, in a part only this
                                        // pom asks for.
                                        + "<name>n</name><scm><url>https://s.example.org/</url></scm>"
                                        + repository(
                                                "r",
                                                "https://r.example.org/${project.scm.url}/${project.name}")))
                .settings(
                        """
                        <settings>
                          <servers><server><id>r</id><password>hunter2</password></server></servers>
                        </settings>
                        """);
        return f;
    }

    /** Harm prevented: the plugin works in the tests and fails where it is installed. */
    @Test
    void theExecutableAnswersOverItsSocketAsTheCodeDoes() throws IOException {
        Fixture f = checkout();

        try (InstalledEcosystem plugin = new InstalledEcosystem(executable(), root)) {
            Detection answered = plugin.detect(f.checkout, f.home);

            assertThat(plugin.ecosystem()).as("what it is installed under").isEqualTo("maven");
            assertThat(answered)
                    .as("the same answer as on the JVM, reflection and all")
                    .isEqualTo(new MavenEcosystem().detect(f.checkout, f.home));
            assertThat(answered.asMap().toString()).doesNotContain("hunter2");
        }
    }

    /** Harm prevented: the settings a task needs never arrive from the installed plugin. */
    @Test
    void theExecutableWritesTheSettingsAsTheCodeDoes() throws IOException {
        List<Repository> asked = List.of(new Repository("corp", "http://127.0.0.1:9420/corp/"));

        try (InstalledEcosystem plugin = new InstalledEcosystem(executable(), root)) {
            List<File> files = plugin.configure(asked);

            assertThat(files).as("the same file").isEqualTo(new MavenEcosystem().configure(asked));
        }
    }
}
