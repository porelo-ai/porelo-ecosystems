package org.fuin.porelo.ecosystem.shared;

import static org.fuin.porelo.ecosystem.shared.VendorJson.address;
import static org.fuin.porelo.ecosystem.shared.VendorJson.asset;
import static org.fuin.porelo.ecosystem.shared.VendorJson.githubDigest;
import static org.fuin.porelo.ecosystem.shared.VendorJson.map;
import static org.fuin.porelo.ecosystem.shared.VendorJson.text;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Finds the shfmt a build pins, from its vendor's GitHub release. */
public final class ShfmtDownloads {
    // It stands in the release's address, so nothing but a plain version is asked.
    private static final Pattern VERSION = Pattern.compile("\\d{1,4}(\\.\\d{1,4}){1,3}");
    private static final String RELEASES = "https://api.github.com/repos/mvdan/sh/releases/tags/v";
    private static final String SUMS = "sha256sums.txt";

    private final VendorFetch fetch;

    /**
     * Asks the vendor through one fetch.
     *
     * @param fetch how an address is read
     */
    public ShfmtDownloads(VendorFetch fetch) {
        this.fetch = fetch;
    }

    /**
     * The Linux x86-64 binary of exactly one version.
     *
     * @param version the version as Spotless takes it, such as {@code 3.12.0}
     * @return its binary
     * @throws ToolUnavailable when the release, its binary or its digest is not found
     */
    public ToolDownload shfmt(String version) {
        if (!VERSION.matcher(version).matches()) {
            throw new ToolUnavailable("no plain version of shfmt: " + version);
        }
        Map<?, ?> release = map(VendorJson.parse(fetch, URI.create(RELEASES + version)));
        if (Boolean.TRUE.equals(release.get("prerelease"))
                || Boolean.TRUE.equals(release.get("draft"))
                || !(release.get("assets") instanceof List<?> assets)) {
            throw new ToolUnavailable("shfmt names no stable release " + version);
        }
        String name = "shfmt_v" + version + "_linux_amd64";
        Map<?, ?> binary =
                asset(assets, name)
                        .orElseThrow(
                                () ->
                                        new ToolUnavailable(
                                                "shfmt's release "
                                                        + version
                                                        + " holds no Linux x86-64 binary"));
        return new ToolDownload(
                version,
                text(binary.get("browser_download_url")),
                githubDigest(binary).orElseGet(() -> fromSums(assets, name)));
    }

    // A release before GitHub named digests lists them in one file beside the binaries.
    private String fromSums(List<?> assets, String name) {
        Optional<Map<?, ?>> sums = asset(assets, SUMS);
        if (sums.isPresent()) {
            String url = text(sums.get().get("browser_download_url"));
            if (url.startsWith("https://github.com/")) {
                for (String line : fetch.answer(address(url)).lines().toList()) {
                    String[] parts = line.strip().split("\\s+");
                    if (parts.length == 2
                            && (parts[1].equals(name) || parts[1].equals("*" + name))) {
                        return parts[0];
                    }
                }
            }
        }
        throw new ToolUnavailable("shfmt's release names no digest of " + name);
    }
}
