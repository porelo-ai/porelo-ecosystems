package org.fuin.porelo.ecosystem.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Harm prevented: a tool's name written by a later adapter runs as a command in the image, as root.
 */
class ToolLinesTest {
    private static final ToolDownload TOOL =
            new ToolDownload("21", "https://github.com/a.tgz", "a".repeat(64));

    @ParameterizedTest
    @ValueSource(strings = {"", "java;rm -rf /", "../etc", "a b", "java/bin", "Java", "-java"})
    void aNameThatIsNoPlainFolderNameIsRefused(String name) {
        assertThatThrownBy(() -> ToolLines.install(name, TOOL))
                .as("'%s' would leave the folder under /opt, or the line", name)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolLines.installBeside(name, TOOL))
                .as("'%s' beside another tool too", name)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"java", "maven", "graalvm", "node-22"})
    void aPlainFolderNameIsTaken(String name) {
        assertThat(ToolLines.install(name, TOOL))
                .as("the tool goes under its own folder")
                .contains("mkdir -p /opt/" + name + ";");
    }
}
