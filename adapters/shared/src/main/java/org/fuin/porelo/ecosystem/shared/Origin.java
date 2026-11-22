package org.fuin.porelo.ecosystem.shared;

/** Where a value came from. */
public enum Origin {
    /** The person's own files, such as their build tool's settings. */
    YOURS,
    /**
     * Neither the person nor the checkout: what a tool's own cache or its built-in default holds.
     */
    MACHINE,
    /** The checkout. */
    CHECKOUT
}
