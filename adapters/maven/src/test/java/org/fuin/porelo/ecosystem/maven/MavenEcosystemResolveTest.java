package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.fuin.porelo.ecosystem.api.Finding;
import org.junit.jupiter.api.Test;

/** What {@code Resolve} hands the vendors' client, and what it answers when that cannot start. */
class MavenEcosystemResolveTest {
    private static final Path NOWHERE = Path.of("/nowhere");

    private static final List<Finding> ASKED =
            List.of(
                    new Finding(
                            Finding.Part.TOOLS,
                            "package.maven",
                            "maven",
                            "pom.xml",
                            3,
                            Finding.Origin.CHECKOUT,
                            false,
                            "what Detect said"));

    /** Harm prevented: behind a proxy, no lookup ever reaches a vendor. */
    @Test
    void theProxyVariablesPoreloPassesReachTheVendorsClientAndNothingElse() {
        Map<String, String> proxies =
                Map.of(
                        "HTTP_PROXY", "http://a.example.org:3128",
                        "HTTPS_PROXY", "http://b.example.org:3128",
                        "NO_PROXY", ".c.example.org",
                        "http_proxy", "http://d.example.org:3128",
                        "https_proxy", "http://e.example.org:3128",
                        "no_proxy", ".f.example.org");
        Map<String, String> environment = new java.util.HashMap<>(proxies);
        environment.put("GITHUB_TOKEN", "never-passed");
        List<Map<String, String>> handed = new ArrayList<>();

        Optional<List<Finding>> answered =
                new MavenEcosystem(
                                environment::get,
                                given -> {
                                    handed.add(given);
                                    return uri -> {
                                        throw new IOException("offline");
                                    };
                                })
                        .resolve(NOWHERE, NOWHERE, ASKED);

        assertThat(handed)
                .as("the proxy settings, as they stand in the environment")
                .containsExactly(proxies);
        assertThat(answered).as("an answer, never \"cannot say\"").isPresent();
    }

    /** Harm prevented: a proxy's password in the draft, or no answer at all. */
    @Test
    void aProxyThatCannotBeReadLeavesTheToolAsDetectedAndSaysWhyWithoutItsValue() {
        Map<String, String> environment = Map.of("HTTPS_PROXY", "http://user:s3cret@pro xy:1");

        List<Finding> answered =
                new MavenEcosystem(environment::get, VendorHttp::new)
                        .resolve(NOWHERE, NOWHERE, ASKED)
                        .orElseThrow();

        assertThat(answered)
                .as("the package stays, so the project still gets a Maven")
                .singleElement()
                .satisfies(
                        f -> {
                            assertThat(f.value()).as("as detected").isEqualTo("maven");
                            assertThat(f.note())
                                    .as("why nothing was found")
                                    .contains("HTTPS_PROXY could not be read");
                        });
        assertThat(answered.toString()).as("the password").doesNotContain("s3cret");
    }
}
