package org.fuin.porelo.ecosystem.maven;

import org.jspecify.annotations.Nullable;

/**
 * The server of the person's settings that shares a repository's id; no value is ever read. A match
 * says no credential is sent: Maven can hold one back from a host the server is not declared for.
 *
 * @param id the id Maven looks a server up by
 * @param known whether the person's settings name a server with that id
 * @param place the server's place when known, else the repository's
 * @param credential what the server sends, when it sends anything
 */
public record ServerReference(
        String id, boolean known, Place place, @Nullable Credential credential) {

    /** A server known or not, sending nothing Porelo is told of. */
    public ServerReference(String id, boolean known, Place place) {
        this(id, known, place, null);
    }
}
