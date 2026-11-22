package org.fuin.porelo.ecosystem.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Harm prevented: a pinned JDK is read with another major, or from a host no vendor uses. */
class JdkPinTest {
    private static final String DIGEST = "c".repeat(64);

    @Test
    void anOldTemurinsFileNameGivesItsMajor() {
        JdkPin pin =
                JdkPin.of(
                        "https://github.com/adoptium/temurin8-binaries/releases/download/"
                                + "jdk8u392-b08/OpenJDK8U-jdk_x64_linux_hotspot_8u392b08.tar.gz",
                        DIGEST);

        assertThat(pin.major()).as("JDK 8's own naming").isEqualTo(8);
        assertThat(pin.download().version()).isEqualTo("8u392b08");
    }

    @Test
    void anArchiveOfAnotherHostIsRefused() {
        assertThatThrownBy(
                        () ->
                                JdkPin.of(
                                        "https://cdn.example.org/OpenJDK21U-jdk_x64_linux_hotspot_21.0.4_7.tar.gz",
                                        DIGEST))
                .as("only a vendor's own host is written into the image")
                .isInstanceOf(ToolUnavailable.class);
    }

    @Test
    void anotherPlatformsArchiveIsNoLinuxJdk() {
        assertThat(
                        JdkPin.isLinuxJdk(
                                "https://github.com/graalvm/graalvm-ce-builds/releases/download/"
                                        + "graal-25.5.5.0.3/graalvm-community-jdk-25i5-25.0.5.0.3_windows-x64_bin.zip"))
                .isFalse();
    }
}
