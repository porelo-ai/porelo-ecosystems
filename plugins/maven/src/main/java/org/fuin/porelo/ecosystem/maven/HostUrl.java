package org.fuin.porelo.ecosystem.maven;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/** A URL reduced to what may be shown: user info, query and fragment can hold secrets. */
record HostUrl(String host, String url) {
    static Optional<HostUrl> parse(String written) {
        try {
            // Encoded as written: what a decoded path holds can reorder a terminal's text.
            URI uri = new URI(new URI(written.strip()).toASCIIString());
            String host = uri.getHost();
            if (host == null
                    || uri.getScheme() == null
                    || uri.getScheme().equalsIgnoreCase("file")) {
                return Optional.empty();
            }
            String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            return Optional.of(
                    new HostUrl(
                            host.toLowerCase(Locale.ROOT),
                            uri.getScheme() + "://" + host + port + path));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    static boolean isLocalFile(String written) {
        return written.strip().toLowerCase(Locale.ROOT).startsWith("file:");
    }
}
