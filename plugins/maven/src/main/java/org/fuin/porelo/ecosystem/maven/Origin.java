package org.fuin.porelo.ecosystem.maven;

/** Who wrote a value, ordered from the most trusted to the least. */
public enum Origin {
    /** The person's own Maven settings. */
    YOURS,
    /**
     * Neither the person nor the checkout: a parent in the local repository, or Maven's default.
     */
    MACHINE,
    /** The checkout, which anybody who can push wrote. */
    CHECKOUT;

    /** The less trusted of this origin and another. */
    Origin leastTrusted(Origin other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
