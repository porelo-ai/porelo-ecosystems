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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Finds the newest stable JDK of a major, from each JDK's own vendor. */
public final class JdkDownloads {
    // 25i5-25.0.5.0.3 since GraalVM split its own releases, 25.0.2 before: read in both forms.
    static final Pattern GRAALVM_ASSET =
            Pattern.compile(
                    "graalvm-community-jdk-(?:(\\d{1,4})i\\d+-)?((\\d{1,4})(?:\\.[0-9.]*)?)_linux-x64_bin\\.tar\\.gz");

    private final VendorFetch fetch;

    /**
     * Asks the vendors through one fetch.
     *
     * @param fetch how an address is read
     */
    public JdkDownloads(VendorFetch fetch) {
        this.fetch = fetch;
    }

    /**
     * The newest Temurin of a major.
     *
     * @param major the JDK's major version
     * @return its archive
     * @throws ToolUnavailable when the vendor names none, which it says with an empty list
     */
    public ToolDownload temurin(int major) {
        URI uri =
                URI.create(
                        "https://api.adoptium.net/v3/assets/latest/"
                                + major
                                + "/hotspot?architecture=x64&image_type=jdk&os=linux"
                                + "&vendor=eclipse");
        if (!(parse(uri) instanceof List<?> rows) || rows.isEmpty()) {
            throw new ToolUnavailable("Temurin names no JDK " + major);
        }
        Map<?, ?> row = map(rows.get(0));
        Map<?, ?> archive = map(map(row.get("binary")).get("package"));
        return new ToolDownload(
                text(map(row.get("version")).get("semver")),
                text(archive.get("link")),
                text(archive.get("checksum")));
    }

    /**
     * The newest stable GraalVM CE of a major.
     *
     * @param major the JDK's major version
     * @return its archive
     * @throws ToolUnavailable when none is listed, or the list is refused
     */
    public ToolDownload graalvm(int major) {
        URI uri =
                URI.create(
                        "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases"
                                + "?per_page=100");
        if (!(parse(uri) instanceof List<?> releases)) {
            throw new ToolUnavailable("GitHub answered no list of GraalVM's releases");
        }
        for (Object each : releases) {
            Map<?, ?> release = map(each);
            if (Boolean.TRUE.equals(release.get("prerelease"))
                    || Boolean.TRUE.equals(release.get("draft"))
                    || !(release.get("assets") instanceof List<?> assets)) {
                continue;
            }
            for (Object one : assets) {
                Map<?, ?> asset = map(one);
                Matcher name = GRAALVM_ASSET.matcher(text(asset.get("name")));
                if (name.matches()
                        && Integer.parseInt(name.group(1) != null ? name.group(1) : name.group(3))
                                == major) {
                    return new ToolDownload(
                            name.group(2),
                            text(asset.get("browser_download_url")),
                            graalvmDigest(asset, assets));
                }
            }
        }
        throw new ToolUnavailable("GraalVM names no release for JDK " + major);
    }

    // A release before GitHub named digests holds it in a file of its own beside the archive.
    private String graalvmDigest(Map<?, ?> archive, List<?> assets) {
        Optional<String> named = githubDigest(archive);
        if (named.isPresent()) {
            return named.get();
        }
        Optional<Map<?, ?>> beside = asset(assets, text(archive.get("name")) + ".sha256");
        if (beside.isPresent()) {
            String url = text(beside.get().get("browser_download_url"));
            if (url.startsWith("https://github.com/")) {
                return fetch.answer(address(url)).strip().split("\\s+")[0];
            }
        }
        throw new ToolUnavailable("GraalVM's release names no digest");
    }

    private @Nullable Object parse(URI uri) {
        return VendorJson.parse(fetch, uri);
    }
}
