package org.fuin.porelo.ecosystem.maven;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/** A URL reduced to what may be shown: user info, query and fragment can hold secrets. */
record HostUrl(String host, String url) {
    static Optional<HostUrl> parse(String written) {
        try {
            URI uri = new URI(written.strip());
            String host = uri.getHost();
            if (host == null
                    || uri.getScheme() == null
                    || uri.getScheme().equalsIgnoreCase("file")) {
                return Optional.empty();
            }
            URI bare =
                    new URI(uri.getScheme(), null, host, uri.getPort(), uri.getPath(), null, null);
            return Optional.of(new HostUrl(host.toLowerCase(Locale.ROOT), bare.toString()));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    static boolean isLocalFile(String written) {
        return written.strip().toLowerCase(Locale.ROOT).startsWith("file:");
    }
}
