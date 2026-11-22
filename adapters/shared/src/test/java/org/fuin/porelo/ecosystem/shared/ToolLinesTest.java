package org.fuin.porelo.ecosystem.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
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
        assertThatThrownBy(() -> ToolLines.installProgram(name, TOOL))
                .as("'%s' would leave /usr/local/bin, or the line", name)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aProgramIsCheckedBeforeItReachesThePath() {
        ToolDownload shfmt =
                new ToolDownload(
                        "3.12.0",
                        "https://github.com/mvdan/sh/releases/download/v3.12.0/shfmt_v3.12.0_linux_amd64",
                        "d".repeat(64));

        assertThat(ToolLines.installProgram("shfmt", shfmt))
                .as("fetched aside, checked, and only then installed where the build finds it")
                .isEqualTo(
                        "RUN set -eu; curl -fsSL -o /tmp/shfmt"
                                + " 'https://github.com/mvdan/sh/releases/download/v3.12.0/shfmt_v3.12.0_linux_amd64';"
                                + " echo '"
                                + "d".repeat(64)
                                + "  /tmp/shfmt' | sha256sum -c -;"
                                + " install -m 0755 /tmp/shfmt /usr/local/bin/shfmt; rm /tmp/shfmt;");
    }

    @ParameterizedTest
    @ValueSource(strings = {"java", "maven", "graalvm", "node-22"})
    void aPlainFolderNameIsTaken(String name) {
        assertThat(ToolLines.install(name, TOOL))
                .as("the tool goes under its own folder")
                .contains("mkdir -p /opt/" + name + ";");
    }
}
