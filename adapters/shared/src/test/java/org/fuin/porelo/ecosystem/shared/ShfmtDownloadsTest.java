package org.fuin.porelo.ecosystem.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Answers shaped as GitHub's release of mvdan/sh was measured: each asset with its name, its
 * address and, since GitHub names them, its digest; older releases with a sums file beside.
 *
 * <p>Harm prevented: Spotless checks the scripts against another shfmt than the build pins, or the
 * image runs a binary nobody checked.
 */
class ShfmtDownloadsTest {
    private static final String RELEASE = "https://api.github.com/repos/mvdan/sh/releases/tags/v";
    private static final String DOWNLOAD = "https://github.com/mvdan/sh/releases/download/v";
    private static final String DIGEST =
            "d9fbb2a9c33d13f47e7618cf362a914d029d02a6df124064fff04fd688a745ea";
    private static final String OTHER =
            "5f3fe3fa6a9f766e6a182ba79a94bef8afedafc57db0b1ad32b0f67fae971ba4";

    private final Map<String, String> answers = new HashMap<>();
    private final List<String> asked = new ArrayList<>();
    private final ShfmtDownloads tools =
            new ShfmtDownloads(
                    uri -> {
                        asked.add(uri.toString());
                        String answer = answers.get(uri.toString());
                        if (answer == null) {
                            throw new IOException("no such answer: " + uri);
                        }
                        return answer;
                    });

    private static String asset(String version, String name, String digest) {
        return """
                {"url":"https://api.github.com/repos/mvdan/sh/releases/assets/1","id":1,
                 "name":"%s","label":"","content_type":"application/octet-stream",
                 "state":"uploaded","size":2916536,"digest":%s,"download_count":5,
                 "browser_download_url":"%s%s/%s"}
                """
                .formatted(name, digest, DOWNLOAD, version, name);
    }

    private void release(String version, boolean prerelease, String... assets) {
        answers.put(
                RELEASE + version,
                """
                {"tag_name":"v%s","prerelease":%s,"draft":false,"assets":[%s]}
                """
                        .formatted(version, prerelease, String.join(",", assets)));
    }

    @Test
    void thePinnedReleaseGivesItsLinuxBinaryAndTheDigestGitHubNames() {
        release(
                "3.12.0",
                false,
                asset("3.12.0", "shfmt_v3.12.0_linux_arm64", "\"sha256:" + OTHER + "\""),
                asset("3.12.0", "shfmt_v3.12.0_linux_amd64", "\"sha256:" + DIGEST + "\""));

        assertThat(tools.shfmt("3.12.0"))
                .as("exactly the version pinned, for the image's machine")
                .isEqualTo(
                        new ToolDownload(
                                "3.12.0", DOWNLOAD + "3.12.0/shfmt_v3.12.0_linux_amd64", DIGEST));
    }

    @Test
    void aReleaseBeforeGitHubNamedDigestsIsCheckedAgainstItsSumsFile() {
        release(
                "3.8.0",
                false,
                asset("3.8.0", "sha256sums.txt", "null"),
                asset("3.8.0", "shfmt_v3.8.0_linux_amd64", "null"));
        answers.put(
                DOWNLOAD + "3.8.0/sha256sums.txt",
                OTHER + "  shfmt_v3.8.0_linux_arm64\n" + DIGEST + "  shfmt_v3.8.0_linux_amd64\n");

        assertThat(tools.shfmt("3.8.0").digest())
                .as("the line of the binary itself, not another's")
                .isEqualTo(DIGEST);
    }

    @Test
    void aReleaseNamingNoDigestIsNeverInstalledUnchecked() {
        release("3.8.0", false, asset("3.8.0", "shfmt_v3.8.0_linux_amd64", "null"));

        assertThatThrownBy(() -> tools.shfmt("3.8.0"))
                .as("without a digest the binary is not taken")
                .isInstanceOf(ToolUnavailable.class)
                .hasMessageContaining("digest");
    }

    @Test
    void aSumsFileWithoutTheBinaryLeavesItUnavailable() {
        release(
                "3.8.0",
                false,
                asset("3.8.0", "sha256sums.txt", "null"),
                asset("3.8.0", "shfmt_v3.8.0_linux_amd64", "null"));
        answers.put(DOWNLOAD + "3.8.0/sha256sums.txt", OTHER + "  shfmt_v3.8.0_linux_arm64\n");

        assertThatThrownBy(() -> tools.shfmt("3.8.0"))
                .as("another binary's digest would check nothing")
                .isInstanceOf(ToolUnavailable.class);
    }

    @Test
    void aDigestOfAnotherKindLeavesItUnavailable() {
        release(
                "3.12.0",
                false,
                asset("3.12.0", "shfmt_v3.12.0_linux_amd64", "\"md5:" + "a".repeat(32) + "\""));

        assertThatThrownBy(() -> tools.shfmt("3.12.0"))
                .as("only a digest sha256sum checks")
                .isInstanceOf(ToolUnavailable.class);
    }

    @Test
    void aPrereleaseIsNotTakenForTheVersion() {
        release(
                "3.13.0",
                true,
                asset("3.13.0", "shfmt_v3.13.0_linux_amd64", "\"sha256:" + DIGEST + "\""));

        assertThatThrownBy(() -> tools.shfmt("3.13.0"))
                .as("a stable release only")
                .isInstanceOf(ToolUnavailable.class);
    }

    @Test
    void aReleaseWithoutTheLinuxBinaryLeavesItUnavailable() {
        release(
                "3.12.0",
                false,
                asset("3.12.0", "shfmt_v3.12.0_linux_arm64", "\"sha256:" + OTHER + "\""));

        assertThatThrownBy(() -> tools.shfmt("3.12.0"))
                .as("another machine's binary would not run")
                .isInstanceOf(ToolUnavailable.class)
                .hasMessageContaining("3.12.0");
    }

    @Test
    void aVendorThatDoesNotAnswerLeavesItUnavailable() {
        assertThatThrownBy(() -> tools.shfmt("3.12.0"))
                .as("offline reads as unknown, and the failure names only the host")
                .isInstanceOf(ToolUnavailable.class)
                .hasMessageNotContaining("no such answer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "latest", "3.12.0/../../x", "3.12.0?x", "v3.12.0", "3"})
    void aVersionThatIsNoPlainVersionIsNeverAsked(String version) {
        assertThatThrownBy(() -> tools.shfmt(version))
                .as("'%s' would ask another address than the release's", version)
                .isInstanceOf(ToolUnavailable.class);
        assertThat(asked).as("nothing is fetched for it").isEmpty();
    }
}
