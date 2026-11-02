package org.fuin.porelo.ecosystem.maven;

/**
 * A property a value was filled in from.
 *
 * @param name the property's name
 * @param place where the value that won stands
 */
public record PropertyUse(String name, Place place) {}
