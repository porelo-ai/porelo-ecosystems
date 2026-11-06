package org.fuin.porelo.ecosystem.maven;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A tool's archive as its vendor names it, holding only what is safe to write into lines a build
 * runs as root.
 *
 * @param version The exact version.
 * @param url Where it is fetched from, by {@code https} from a known host.
 * @param digest Its hex digest, from the same source as the archive.
 */
record ToolDownload(String version, String url, String digest) {
    private static final Set<String> HOSTS = Set.of("github.com", "repo.maven.apache.org");
    private static final Pattern VERSION = Pattern.compile("[0-9A-Za-z][0-9A-Za-z.+_-]{0,39}");
    private static final Pattern URL = Pattern.compile("[0-9A-Za-z.~%/+:_-]{1,300}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}|[0-9a-f]{128}");

    /** Takes what a vendor answered only when each part holds what it should. */
    ToolDownload {
        if (!VERSION.matcher(version).matches()) {
            throw new ToolUnavailable("a version that is no plain version");
        }
        if (!URL.matcher(url).matches() || !fromKnownHost(url)) {
            throw new ToolUnavailable("an address that is no known download");
        }
        if (!DIGEST.matcher(digest).matches()) {
            throw new ToolUnavailable("a digest that is no hex digest");
        }
    }

    /** The command that checks this digest. */
    String checkCommand() {
        return digest.length() == 64 ? "sha256sum" : "sha512sum";
    }

    private static boolean fromKnownHost(String url) {
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme())
                    && uri.getHost() != null
                    && HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    && uri.getPort() == -1;
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
