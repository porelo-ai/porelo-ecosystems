package org.fuin.porelo.ecosystem.shared;

/** A vendor gave no usable answer, so the tool stays what was asked and nothing is installed. */
public final class ToolUnavailable extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final boolean openJdk;

    /**
     * Says why.
     *
     * @param why why, naming no value a vendor answered
     */
    public ToolUnavailable(String why) {
        this(why, false);
    }

    ToolUnavailable(String why, boolean openJdk) {
        super(why);
        this.openJdk = openJdk;
    }

    /**
     * Whether the JDK was refused as open to Java4Shell, so that no distribution's package of the
     * same release stands in for it either.
     *
     * @return true when so
     */
    public boolean openJdk() {
        return openJdk;
    }
}
