package org.fuin.porelo.ecosystem.maven;

/** Text a forge can write, made safe to show: it reaches a person's terminal. */
final class SafeText {
    private static final int MAX_LENGTH = 200;

    private SafeText() {}

    static String of(String text) {
        String printable = text.replaceAll("\\p{Cc}|\\p{Cf}", "?");
        return printable.length() <= MAX_LENGTH
                ? printable
                : printable.substring(0, MAX_LENGTH) + "...";
    }
}
