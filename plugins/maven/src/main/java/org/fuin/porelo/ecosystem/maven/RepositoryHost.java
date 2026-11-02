package org.fuin.porelo.ecosystem.maven;

/**
 * A host a build must reach.
 *
 * @param id the repository's or mirror's id
 * @param host the host name, lower case
 * @param url the URL without user info, query or fragment
 * @param kind what names it
 * @param place the line of its URL
 * @param server the server its id refers to
 */
public record RepositoryHost(
        String id, String host, String url, Kind kind, Place place, ServerReference server) {

    /** What names a host. */
    public enum Kind {
        /** A {@code <repository>}. */
        REPOSITORY,
        /** A {@code <pluginRepository>}. */
        PLUGIN_REPOSITORY,
        /** A settings {@code <mirror>}, which replaces what it mirrors. */
        MIRROR
    }

    /** Who wrote the line of its URL. */
    public Origin origin() {
        return place.origin();
    }
}
