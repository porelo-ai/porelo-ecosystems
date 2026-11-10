package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CopyrightTest {

    /** What the build compiles into the executable, as dependency:build-classpath wrote it. */
    private static List<String> compiledIn() throws Exception {
        final String file = System.getProperty("test.runtime.classpath");
        assertThat(file).as("the build names the runtime classpath it wrote").isNotBlank();
        return Arrays.stream(
                        Files.readString(Path.of(file), StandardCharsets.UTF_8)
                                .strip()
                                .split("[" + File.pathSeparator + "\n]"))
                .filter(each -> !each.isBlank())
                .map(each -> Path.of(each.strip()).getFileName().toString())
                .toList();
    }

    /** The artifact a jar is named after, {@code <artifact>-<version>[-<classifier>].jar}. */
    static String artifact(final String jar) {
        return jar.replaceFirst("-\\d.*\\.jar$", "");
    }

    // A library compiled in that the copyright file leaves out ships with its license unsaid.
    @Test
    void theDebianCopyrightNamesEveryLibraryCompiledIn() throws Exception {
        final String copyright =
                Files.readString(Path.of("src/deb/copyright/copyright"), StandardCharsets.UTF_8);
        final List<String> jars = compiledIn();

        assertThat(jars).as("the classpath is read").isNotEmpty();
        for (final String jar : jars) {
            assertThat(copyright)
                    .as("the copyright names %s", jar)
                    .contains(" " + artifact(jar) + " ");
        }
        assertThat(copyright)
                .as("and says where their texts stand in full")
                .contains("/usr/share/doc/porelo-ecosystem-maven/THIRD-PARTY-NOTICES");
    }

    // A version with a qualifier or a classifier must not hide a library from the check above.
    @Test
    void anArtifactIsReadWhateverItsVersionCarries() {
        assertThat(artifact("snakeyaml-2.7.jar")).isEqualTo("snakeyaml");
        assertThat(artifact("porelo-wire-0.5.1-SNAPSHOT.jar")).isEqualTo("porelo-wire");
        assertThat(artifact("guava-33.4.0-jre.jar")).isEqualTo("guava");
        assertThat(artifact("maven-api-core-4.0.0-alpha-13.jar")).isEqualTo("maven-api-core");
        assertThat(artifact("netty-transport-native-epoll-4.1.100.Final-linux-x86_64.jar"))
                .isEqualTo("netty-transport-native-epoll");
    }

    // A wrapped License line in a Files paragraph is read as its synopsis plus license text: the
    // licenses on the second line would drop out of what a parser names.
    @Test
    void theExecutablesLicensesStandOnOneLine() throws Exception {
        final List<String> lines =
                Files.readAllLines(Path.of("src/deb/copyright/copyright"), StandardCharsets.UTF_8);
        final int files = lines.indexOf("Files: usr/libexec/porelo/ecosystems/maven");
        assertThat(files).as("the executable has its paragraph").isNotNegative();
        final int license = files + 2;
        assertThat(lines.get(license))
                .startsWith("License: ")
                .contains(
                        "GPL-3.0-or-later",
                        "Apache-2.0",
                        "EPL-2.0",
                        "BSD-3-Clause",
                        "MIT",
                        "BSD-2-Clause",
                        "xpp",
                        "GPL-2.0-only with Classpath-2.0 exception");
        assertThat(lines.get(license + 1)).as("no license text follows").startsWith("Comment:");
    }
}
