package org.fuin.porelo.ecosystem.shared;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Finds the newest stable JDK of a major, from each JDK's own vendor. */
public final class JdkDownloads {
    // 25i5-25.0.5.0.3 since GraalVM split its own releases, 25.0.2 before: read in both forms.
    private static final Pattern GRAALVM_ASSET =
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
        if (archive.get("digest") != null) {
            String named = text(archive.get("digest"));
            if (!named.startsWith("sha256:")) {
                throw new ToolUnavailable("a digest of another kind");
            }
            return named.substring("sha256:".length());
        }
        String beside = text(archive.get("name")) + ".sha256";
        for (Object one : assets) {
            Map<?, ?> asset = map(one);
            if (beside.equals(asset.get("name"))) {
                String url = text(asset.get("browser_download_url"));
                if (url.startsWith("https://github.com/")) {
                    return fetch.answer(address(url)).strip().split("\\s+")[0];
                }
            }
        }
        throw new ToolUnavailable("GraalVM's release names no digest");
    }

    private @Nullable Object parse(URI uri) {
        try {
            return new Yaml(new SafeConstructor(answerOptions())).load(fetch.answer(uri));
        } catch (YAMLException e) {
            throw new ToolUnavailable(uri.getHost() + " answered no JSON");
        }
    }

    // JSON read as YAML: as long as an answer may be, and a key named twice is no answer.
    private static LoaderOptions answerOptions() {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(VendorHttp.MOST_BYTES);
        options.setAllowDuplicateKeys(false);
        // A tag or a number the constructor cannot build throws past YAMLException otherwise.
        options.setWrappedToRootException(true);
        return options;
    }

    private static URI address(String url) {
        try {
            return new URI(url);
        } catch (java.net.URISyntaxException e) {
            throw new ToolUnavailable("GitHub named a digest at no address");
        }
    }

    private static Map<?, ?> map(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new ToolUnavailable("a vendor's answer is not shaped as measured");
    }

    private static String text(@Nullable Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw new ToolUnavailable("a vendor's answer is not shaped as measured");
    }
}
