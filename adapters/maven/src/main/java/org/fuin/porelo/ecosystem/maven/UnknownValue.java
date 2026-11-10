package org.fuin.porelo.ecosystem.maven;

/**
 * A value that could not be answered, never guessed.
 *
 * @param what what is unknown: a property, coordinates, a profile or a file
 * @param why why, and what would settle it
 * @param place where it is asked for
 */
public record UnknownValue(String what, String why, Place place) {}
