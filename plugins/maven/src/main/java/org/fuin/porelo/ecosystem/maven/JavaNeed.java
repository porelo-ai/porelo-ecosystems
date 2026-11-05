package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which JDK major versions one statement of a build allows, read as a Maven version range; a bare
 * version allows itself and everything above, as both the compiler's level and the enforcer mean
 * it.
 *
 * @param written What the build states, as it stands there.
 * @param ranges The majors allowed, none of them empty.
 */
record JavaNeed(String written, List<Range> ranges) {
    private static final Pattern RANGE = Pattern.compile("([\\[(])([^\\])]*)([\\])])");
    private static final Pattern VERSION = Pattern.compile("(\\d{1,4})(?:\\.(\\d{1,4}))?(.*)");

    /** Majors from {@code low} up to {@code high}, both included. */
    record Range(int low, int high) {}

    /**
     * Reads what a build states.
     *
     * @param written A bare version or a range set, such as {@code 17} or {@code (,8],[21,)}.
     * @return The need, or empty when this names no version.
     */
    static Optional<JavaNeed> of(String written) {
        String text = written.trim();
        if (!text.startsWith("[") && !text.startsWith("(")) {
            return major(text)
                    .map(low -> new JavaNeed(written, List.of(new Range(low, Integer.MAX_VALUE))));
        }
        List<Range> ranges = new ArrayList<>();
        Matcher range = RANGE.matcher(text);
        while (range.find()) {
            Range read = range(range.group(1), range.group(2), range.group(3));
            if (read.low() <= read.high()) {
                ranges.add(read);
            }
        }
        return ranges.isEmpty() && !RANGE.matcher(text).find()
                ? Optional.empty()
                : Optional.of(new JavaNeed(written, List.copyOf(ranges)));
    }

    /** Whether a JDK of this major meets it. */
    boolean allows(int major) {
        return ranges.stream().anyMatch(r -> r.low() <= major && major <= r.high());
    }

    /** The lowest major it allows, 0 when its start is open, or when it allows none. */
    int lowest() {
        return ranges.stream().mapToInt(Range::low).min().orElse(0);
    }

    private static Range range(String open, String body, String close) {
        int comma = body.indexOf(',');
        String start = comma < 0 ? body : body.substring(0, comma);
        String end = comma < 0 ? body : body.substring(comma + 1);
        int low = major(start).orElse(0);
        int high = major(end).orElse(Integer.MAX_VALUE);
        // Only a bare major is left out whole: (,17.0.3) still holds a JDK 17.
        if ("(".equals(open) && isBareMajor(start)) {
            low++;
        }
        if (")".equals(close) && isBareMajor(end)) {
            high--;
        }
        return new Range(low, high);
    }

    private static boolean isBareMajor(String version) {
        String text = version.trim();
        return text.matches("\\d+") || text.matches("1\\.\\d+");
    }

    // 1.8 is Java 8.
    private static Optional<Integer> major(String version) {
        Matcher matched = VERSION.matcher(version.trim());
        if (!matched.matches()) {
            return Optional.empty();
        }
        int first = Integer.parseInt(matched.group(1));
        return Optional.of(
                first == 1 && matched.group(2) != null
                        ? Integer.parseInt(matched.group(2))
                        : first);
    }
}
