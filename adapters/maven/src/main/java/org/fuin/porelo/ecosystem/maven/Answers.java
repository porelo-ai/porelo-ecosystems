package org.fuin.porelo.ecosystem.maven;

import org.fuin.porelo.ecosystem.api.Finding;

/** Writes a value and its place as the contract takes them. */
final class Answers {
    private Answers() {}

    static Finding tool(String key, String value, Place place, String note) {
        return finding(Finding.Part.TOOLS, key, value, place, false, note);
    }

    static Finding rule(String key, String value, Place place, String note) {
        return finding(Finding.Part.RULES, key, value, place, false, note);
    }

    static Finding unknown(Finding.Part part, String key, Place place, String note) {
        return finding(part, key, "", place, true, note);
    }

    private static Finding finding(
            Finding.Part part,
            String key,
            String value,
            Place place,
            boolean unknown,
            String note) {
        return new Finding(
                part,
                key,
                value,
                place.file(),
                place.file().isEmpty() ? 0 : place.line(),
                // Only the machine may have no file: the contract refuses any other without one.
                place.file().isEmpty() ? Finding.Origin.MACHINE : origin(place.origin()),
                unknown,
                note);
    }

    private static Finding.Origin origin(Origin origin) {
        return switch (origin) {
            case YOURS -> Finding.Origin.YOURS;
            case CHECKOUT -> Finding.Origin.CHECKOUT;
            case MACHINE -> Finding.Origin.MACHINE;
        };
    }
}
