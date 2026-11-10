package org.fuin.porelo.ecosystem.maven;

/** A vendor gave no usable answer, so the tool stays what was asked and nothing is installed. */
final class ToolUnavailable extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ToolUnavailable(String why) {
        super(why);
    }
}
