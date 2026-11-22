package org.fuin.porelo.ecosystem.shared;

/**
 * Where a value stands.
 *
 * @param file relative to the checkout for {@link Origin#CHECKOUT}, else to the home where it lies
 *     there; empty when no file holds it, as for a build tool's built-in defaults
 * @param line the line, 0 when unknown
 * @param origin who wrote it
 */
public record Place(String file, int line, Origin origin) {}
