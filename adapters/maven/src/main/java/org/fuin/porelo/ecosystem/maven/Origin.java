package org.fuin.porelo.ecosystem.maven;

/** Where a value came from. */
public enum Origin {
    /** The person's own Maven settings. */
    YOURS,
    /**
     * Neither the person nor the checkout: a parent in the local repository, or Maven's default.
     */
    MACHINE,
    /** The checkout. */
    CHECKOUT
}
