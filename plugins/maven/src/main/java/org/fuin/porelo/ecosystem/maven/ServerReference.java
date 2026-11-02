package org.fuin.porelo.ecosystem.maven;

/**
 * The credential a repository would be reached with, by its id alone; no value is ever read.
 *
 * @param id the id Maven looks a server up by
 * @param known whether the person's settings name a server with that id
 * @param place the server's place when known, else the repository's
 */
public record ServerReference(String id, boolean known, Place place) {}
