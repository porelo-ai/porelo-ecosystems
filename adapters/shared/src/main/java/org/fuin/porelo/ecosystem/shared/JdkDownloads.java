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
    // 25i6-25.0.5.1 since GraalVM split its own releases, 25.0.2 before: read in both forms.
    static final Pattern GRAALVM_ASSET =
            Pattern.compile(
                    "graalvm-community-jdk-(?:\\d{1,4}i(?<release>\\d{1,4})-)?"
                            + "(?<version>(?<base>\\d{1,4})(?:\\.[0-9.]*)?)_linux-x64_bin\\.tar\\.gz");

    // 1000 assets a release, as GitHub allows, under 2 KiB each: five stay under the 16 MiB bound.
    static final int RELEASES_PER_PAGE = 5;

    private final VendorFetch fetch;

    /**
     * The major of a GraalVM archive's name that {@link #GRAALVM_ASSET} matched.
     *
     * @param name the match
     * @return its JDK's major version, the first part of the JDK's own version
     */
    static int graalvmMajor(Matcher name) {
        return Integer.parseInt(name.group("base"));
    }

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
     * @throws ToolUnavailable when the vendor names none, which it says with an empty list, or its
     *     newest is open to Java4Shell
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
        // Its semver folds a fourth version part into the build number, which openjdk_version
        // keeps.
        String version = text(map(row.get("version")).get("openjdk_version"));
        Java4Shell.refuseOpen("Temurin", major, null, version);
        return new ToolDownload(version, text(archive.get("link")), text(archive.get("checksum")));
    }

    /**
     * The newest stable GraalVM CE of a major.
     *
     * @param major the JDK's major version
     * @return its archive
     * @throws ToolUnavailable when none is listed, the list is refused, or the newest is open to
     *     Java4Shell
     */
    public ToolDownload graalvm(int major) {
        for (int page = 1; ; page++) {
            List<?> releases = graalvmReleases(page);
            Optional<ToolDownload> found = findGraalvm(major, releases);
            if (found.isPresent()) {
                return found.get();
            }
            if (releases.size() < RELEASES_PER_PAGE) {
                throw new ToolUnavailable("GraalVM names no release for JDK " + major);
            }
        }
    }

    private List<?> graalvmReleases(int page) {
        URI uri =
                URI.create(
                        "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases"
                                + "?per_page="
                                + RELEASES_PER_PAGE
                                + "&page="
                                + page);
        if (!(parse(uri) instanceof List<?> releases)) {
            throw new ToolUnavailable("GitHub answered no list of GraalVM's releases");
        }
        return releases;
    }

    private Optional<ToolDownload> findGraalvm(int major, List<?> releases) {
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
                if (name.matches() && graalvmMajor(name) == major) {
                    // The releases come newest first, so an older one is no way out.
                    Java4Shell.refuseOpen(
                            "GraalVM CE", major, name.group("release"), name.group("version"));
                    return Optional.of(
                            new ToolDownload(
                                    name.group("version"),
                                    text(asset.get("browser_download_url")),
                                    graalvmDigest(asset, assets)));
                }
            }
        }
        return Optional.empty();
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
