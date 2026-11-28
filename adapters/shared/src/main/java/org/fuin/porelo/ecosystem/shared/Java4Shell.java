package org.fuin.porelo.ecosystem.shared;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The JDKs open to CVE-2026-114938 (Java4Shell), whose Base64 decoder lets data from outside run
 * code: GraalVM CE 25.1 up to but not including 25.6, and any JDK 25 below 25.0.5.1.
 */
final class Java4Shell {
    private static final int MAJOR = 25;
    private static final int FIRST_OPEN_RELEASE = 1;
    private static final int FIRST_FIXED_RELEASE = 6;
    private static final List<String> FIRST_FIXED_JDK = List.of("25", "0", "5", "1");
    private static final Pattern NUMBERS = Pattern.compile("\\d+(?:\\.\\d+)*");

    private Java4Shell() {}

    /**
     * Refuses a JDK open to Java4Shell.
     *
     * @param vendor whose JDK it is, as said to the person
     * @param major the JDK's major version
     * @param graalvmRelease GraalVM's own release within that major, {@code 5} for 25.5, or null
     *     for another vendor and for a GraalVM file name older than that numbering
     * @param version the JDK's version, such as {@code 25.0.5.1} or {@code 25.0.5.1+2-LTS}
     * @throws ToolUnavailable when it is open
     */
    static void refuseOpen(
            String vendor, int major, @Nullable String graalvmRelease, String version) {
        if (major != MAJOR) {
            return;
        }
        int release = graalvmRelease == null ? 0 : Integer.parseInt(graalvmRelease);
        if (release >= FIRST_OPEN_RELEASE && release < FIRST_FIXED_RELEASE || below(version)) {
            throw new ToolUnavailable(
                    vendor
                            + " "
                            + (graalvmRelease == null
                                    ? version
                                    : MAJOR + "." + graalvmRelease + " (JDK " + version + ")")
                            + " is open to Java4Shell (CVE-2026-114938); for Java 25 only a JDK"
                            + " from 25.0.5.1 is taken, and of GraalVM CE only 25.6 or later",
                    true);
        }
    }

    // Compared part by part as digits, so no part of any length overflows; a missing part is 0.
    private static boolean below(String version) {
        Matcher numbers = NUMBERS.matcher(version);
        List<String> parts =
                numbers.lookingAt() ? List.of(numbers.group().split("\\.")) : List.of();
        for (int i = 0; i < Math.max(parts.size(), FIRST_FIXED_JDK.size()); i++) {
            int order =
                    compare(
                            i < parts.size() ? parts.get(i) : "0",
                            i < FIRST_FIXED_JDK.size() ? FIRST_FIXED_JDK.get(i) : "0");
            if (order != 0) {
                return order < 0;
            }
        }
        return false;
    }

    private static int compare(String left, String right) {
        String a = left.replaceFirst("^0+(?=.)", "");
        String b = right.replaceFirst("^0+(?=.)", "");
        return a.length() != b.length() ? Integer.compare(a.length(), b.length()) : a.compareTo(b);
    }
}
