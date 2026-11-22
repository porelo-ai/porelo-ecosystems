package org.fuin.porelo.ecosystem.shared;

/** A vendor gave no usable answer, so the tool stays what was asked and nothing is installed. */
public final class ToolUnavailable extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Says why.
     *
     * @param why why, naming no value a vendor answered
     */
    public ToolUnavailable(String why) {
        super(why);
    }
}
