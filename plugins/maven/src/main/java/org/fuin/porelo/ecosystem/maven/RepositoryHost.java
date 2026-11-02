package org.fuin.porelo.ecosystem.maven;

import java.util.List;

/**
 * A host a build must reach.
 *
 * @param id the repository's or mirror's id
 * @param host the host name, lower case
 * @param url the URL without user info, query or fragment
 * @param kind what names it
 * @param place where the repository stands
 * @param properties what its URL was filled in from, nested properties included
 * @param server the server its id refers to
 */
public record RepositoryHost(
        String id,
        String host,
        String url,
        Kind kind,
        Place place,
        List<PropertyUse> properties,
        ServerReference server) {

    /** What names a host. */
    public enum Kind {
        /** A {@code <repository>}. */
        REPOSITORY,
        /** A {@code <pluginRepository>}. */
        PLUGIN_REPOSITORY,
        /** A settings {@code <mirror>}, which replaces what it mirrors. */
        MIRROR
    }

    /** Copies the list. */
    public RepositoryHost {
        properties = List.copyOf(properties);
    }

    /** The least trusted of whoever wrote the repository and the properties that filled it in. */
    public Origin origin() {
        Origin least = place.origin();
        for (PropertyUse use : properties) {
            least = least.leastTrusted(use.place().origin());
        }
        return least;
    }
}
