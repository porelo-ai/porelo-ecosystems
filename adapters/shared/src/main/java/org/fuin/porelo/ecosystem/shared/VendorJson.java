package org.fuin.porelo.ecosystem.shared;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** A vendor's JSON answer, read only as far as it is shaped as measured. */
final class VendorJson {
    private static final String SHA256 = "sha256:";

    private VendorJson() {}

    static @Nullable Object parse(VendorFetch fetch, URI uri) {
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

    /** The digest GitHub names for a release asset, or empty for a release from before. */
    static Optional<String> githubDigest(Map<?, ?> asset) {
        if (asset.get("digest") == null) {
            return Optional.empty();
        }
        String named = text(asset.get("digest"));
        if (!named.startsWith(SHA256)) {
            throw new ToolUnavailable("a digest of another kind");
        }
        return Optional.of(named.substring(SHA256.length()));
    }

    /** The asset of a release by its exact name. */
    static Optional<Map<?, ?>> asset(List<?> assets, String name) {
        for (Object one : assets) {
            Map<?, ?> asset = map(one);
            if (name.equals(asset.get("name"))) {
                return Optional.of(asset);
            }
        }
        return Optional.empty();
    }

    static URI address(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new ToolUnavailable("a vendor named a file at no address");
        }
    }

    static Map<?, ?> map(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new ToolUnavailable("a vendor's answer is not shaped as measured");
    }

    static String text(@Nullable Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw new ToolUnavailable("a vendor's answer is not shaped as measured");
    }
}
