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
 * <p>A JDK of a major is taken as packaged, with updates: 25.0.4, not 25. As measured on the
 * enforcer with a JDK 25.0.4, {@code (25,)} and {@code (,25.1)} hold it, while {@code [25]}, {@code
 * [21,25]}, {@code [21,25.0.0]} and {@code [21,25.0.1)} do not. Which update a package brings is
 * not known, so a limit on an update counts as the whole major: below it for an upper limit, in it
 * for a lower one.
 *
 * @param written What the build states, as it stands there.
 * @param ranges The majors allowed; none when it allows no JDK with updates, such as {@code [21]}.
 */
record JavaNeed(String written, List<Range> ranges) {
    private static final String ONE_RANGE = "[\\[(][^\\])]*[\\])]";
    private static final Pattern RANGE_SET = Pattern.compile(ONE_RANGE + "(?:," + ONE_RANGE + ")*");
    private static final Pattern RANGE = Pattern.compile("([\\[(])([^\\])]*)[\\])]");
    private static final Pattern VERSION =
            Pattern.compile("(\\d{1,4})(?:\\.(\\d{1,4}))?(?:\\.(\\d{1,4}))?(?:[._+-].*)?");

    /** Majors from {@code low} up to {@code high}, both included. */
    record Range(int low, int high) {}

    /** A version as far as it decides a major: 1.8.0 is major 8, then 0. */
    private record Bound(int major, int next) {}

    /**
     * Reads what a build states.
     *
     * @param written A bare version or a range set, such as {@code 17} or {@code (,8],[21,)}.
     * @return The need, or empty when this names no version.
     */
    static Optional<JavaNeed> of(String written) {
        String text = written.trim();
        if (!text.startsWith("[") && !text.startsWith("(")) {
            return bound(text)
                    .map(b -> new JavaNeed(written, List.of(new Range(low(b), Integer.MAX_VALUE))));
        }
        if (!RANGE_SET.matcher(text).matches()) {
            return Optional.empty();
        }
        List<Range> ranges = new ArrayList<>();
        Matcher range = RANGE.matcher(text);
        while (range.find()) {
            String body = range.group(2);
            int comma = body.indexOf(',');
            if (comma < 0) {
                // One exact version: no JDK with updates is it, if it reads at all.
                if (bound(body.trim()).isEmpty()) {
                    return Optional.empty();
                }
                continue;
            }
            Optional<Range> read = range(body.substring(0, comma), body.substring(comma + 1));
            if (read.isEmpty()) {
                return Optional.empty();
            }
            if (read.get().low() <= read.get().high()) {
                ranges.add(read.get());
            }
        }
        return Optional.of(new JavaNeed(written, List.copyOf(ranges)));
    }

    /** Whether a JDK of this major meets it. */
    boolean allows(int major) {
        return ranges.stream().anyMatch(r -> r.low() <= major && major <= r.high());
    }

    /** The lowest major it allows, 0 when its start is open, or when it allows none. */
    int lowest() {
        return ranges.stream().mapToInt(Range::low).min().orElse(0);
    }

    private static Optional<Range> range(String start, String end) {
        Optional<Bound> from = start.isBlank() ? Optional.empty() : bound(start.trim());
        Optional<Bound> to = end.isBlank() ? Optional.empty() : bound(end.trim());
        if ((!start.isBlank() && from.isEmpty()) || (!end.isBlank() && to.isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(
                new Range(
                        from.map(JavaNeed::low).orElse(0),
                        to.map(JavaNeed::high).orElse(Integer.MAX_VALUE)));
    }

    // 25 holds 25.0.4 from below, however the bracket stands; 25.1 lies above every 25.
    private static int low(Bound bound) {
        return bound.next() > 0 ? bound.major() + 1 : bound.major();
    }

    // 25 ends below 25.0.4, however the bracket stands; 25.1 lies above every 25.
    private static int high(Bound bound) {
        return bound.next() > 0 ? bound.major() : bound.major() - 1;
    }

    // 1.8 is Java 8.
    private static Optional<Bound> bound(String version) {
        Matcher matched = VERSION.matcher(version);
        if (!matched.matches()) {
            return Optional.empty();
        }
        int first = Integer.parseInt(matched.group(1));
        return Optional.of(
                first == 1 && matched.group(2) != null
                        ? new Bound(Integer.parseInt(matched.group(2)), number(matched.group(3)))
                        : new Bound(first, number(matched.group(2))));
    }

    private static int number(String digits) {
        return digits == null ? 0 : Integer.parseInt(digits);
    }
}
