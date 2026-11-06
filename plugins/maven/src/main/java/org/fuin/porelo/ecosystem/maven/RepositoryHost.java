package org.fuin.porelo.ecosystem.maven;

/**
 * A host a build must reach.
 *
 * @param id the repository's or mirror's id
 * @param host the host name, lower case
 * @param url the URL without user info, query or fragment
 * @param kind what names it
 * @param place the line of its URL
 * @param server the server its id refers to; for a proxy, whether it has a credential of its own
 * @param mirrorOf for a mirror, the repositories it takes as its settings write them; else empty
 */
public record RepositoryHost(
        String id,
        String host,
        String url,
        Kind kind,
        Place place,
        ServerReference server,
        String mirrorOf) {

    /** A host that stands for itself alone. */
    public RepositoryHost(
            String id, String host, String url, Kind kind, Place place, ServerReference server) {
        this(id, host, url, kind, place, server, "");
    }

    /** What names a host. */
    public enum Kind {
        /** A {@code <repository>}. */
        REPOSITORY,
        /** A {@code <pluginRepository>}. */
        PLUGIN_REPOSITORY,
        /** An address a plugin the build runs reaches of its own, such as a Javadoc link. */
        PLUGIN_REACHES,
        /** A settings {@code <mirror>}, which replaces what it mirrors. */
        MIRROR,
        /** A settings {@code <proxy>}, which the build goes through. */
        PROXY
    }

    /** Who wrote the line of its URL. */
    public Origin origin() {
        return place.origin();
    }
}
