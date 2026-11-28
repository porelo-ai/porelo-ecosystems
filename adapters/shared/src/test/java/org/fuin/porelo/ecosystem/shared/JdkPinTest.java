package org.fuin.porelo.ecosystem.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    private static final String GRAALVM =
            "https://github.com/graalvm/graalvm-ce-builds/releases/download/";

    /** Harm prevented: a checkout's pin installs a GraalVM whose Base64 decoder runs data. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "graal-25.5.5.0.3/graalvm-community-jdk-25i5-25.0.5.0.3_linux-x64_bin.tar.gz",
                "graal-25.1.1/graalvm-community-jdk-25i1-25.0.1_linux-x64_bin.tar.gz",
                "jdk-25.0.2/graalvm-community-jdk-25.0.2_linux-x64_bin.tar.gz"
            })
    void aGraalvmPinOpenToJava4ShellIsRefused(String path) {
        assertThatThrownBy(() -> JdkPin.of(GRAALVM + path, DIGEST))
                .as("not taken, and the draft says why")
                .isInstanceOf(ToolUnavailable.class)
                .hasMessageContaining("Java4Shell");
    }

    @Test
    void aTemurinPinOpenToJava4ShellIsRefused() {
        assertThatThrownBy(
                        () ->
                                JdkPin.of(
                                        "https://github.com/adoptium/temurin25-binaries/releases/download/"
                                                + "jdk-25.0.5%2B11/OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_11.tar.gz",
                                        DIGEST))
                .as("HotSpot's decoder is open too")
                .isInstanceOf(ToolUnavailable.class)
                .hasMessageContaining("Java4Shell");
    }

    @Test
    void theFixedTemurinPinIsTaken() {
        JdkPin pin =
                JdkPin.of(
                        "https://github.com/adoptium/temurin25-binaries/releases/download/"
                                + "jdk-25.0.5.1%2B2/OpenJDK25U-jdk_x64_linux_hotspot_25.0.5.1_2.tar.gz",
                        DIGEST);

        assertThat(pin.download().version()).as("the fixed release").isEqualTo("25.0.5.1_2");
    }

    @Test
    void theFixedGraalvmPinIsTaken() {
        JdkPin pin =
                JdkPin.of(
                        GRAALVM
                                + "graal-25.6.5.1/graalvm-community-jdk-25i6-25.0.5.1_linux-x64_bin.tar.gz",
                        DIGEST);

        assertThat(pin.download().version()).as("25.6 is the fixed release").isEqualTo("25.0.5.1");
    }

    /** Harm prevented: the floor leaves a project on another major without its JDK. */
    @Test
    void aGraalvmPinOfAnotherMajorIsTaken() {
        JdkPin pin =
                JdkPin.of(
                        GRAALVM + "jdk-21.0.2/graalvm-community-jdk-21.0.2_linux-x64_bin.tar.gz",
                        DIGEST);

        assertThat(pin.major()).as("the floor is JDK 25's").isEqualTo(21);
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
