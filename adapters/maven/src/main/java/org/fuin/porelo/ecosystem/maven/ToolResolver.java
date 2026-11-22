package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.ecosystem.api.Finding;
import org.fuin.porelo.ecosystem.shared.JdkDownloads;
import org.fuin.porelo.ecosystem.shared.JdkPin;
import org.fuin.porelo.ecosystem.shared.ShfmtDownloads;
import org.fuin.porelo.ecosystem.shared.ToolDownload;
import org.fuin.porelo.ecosystem.shared.ToolLines;
import org.fuin.porelo.ecosystem.shared.ToolUnavailable;
import org.fuin.porelo.ecosystem.shared.VendorFetch;

/**
 * What a project asks, made exact: {@code Detect}'s answer with each tool's distribution package
 * replaced by lines that fetch the newest stable version. A tool whose vendor gives no usable
 * answer keeps its package, with a note saying what was asked, so a project is never left with
 * none.
 */
final class ToolResolver {
    // Asked for nothing, a build still needs a JDK: the newest with long-term support, as measured.
    private static final int NO_JAVA_ASKED = 25;

    private static final Pattern MAJOR = Pattern.compile("\\d{1,4}");
    private static final String MIRROR_OF = "extension.mirrorOf.";
    // Only what cannot leave the quotes of the image's line, nor reach another host.
    private static final Pattern PLAIN_HTTPS =
            Pattern.compile("https://[A-Za-z0-9.-]+(:(\\d{1,5}))?(/[A-Za-z0-9._~/+-]*)?");
    private static final int HIGHEST_PORT = 65_535;

    // Porelo joins run.<name> in the order of the names: the JDK's lines before GraalVM's.
    private static final String JAVA_LINES = "run.1-java";
    private static final String PIN = "jdk.pin.";
    private static final Predicate<Finding> JDK_PACKAGES =
            f ->
                    f.key().equals("package.default-jdk-headless")
                            || (f.key().startsWith("package.openjdk-")
                                    && f.key().endsWith("-jdk-headless"));

    private final JdkDownloads jdks;
    private final MavenDownloads mavens;
    private final ShfmtDownloads shfmts;

    ToolResolver(VendorFetch fetch) {
        this.jdks = new JdkDownloads(fetch);
        this.mavens = new MavenDownloads(fetch);
        this.shfmts = new ShfmtDownloads(fetch);
    }

    /** Answers what replaces {@code findings}. */
    List<Finding> resolve(List<Finding> findings) {
        List<Finding> all = new ArrayList<>(findings);
        Optional<Finding> java = find(findings, "java.version");
        boolean noMajor =
                java.filter(f -> !f.unknown() && !MAJOR.matcher(f.value()).matches()).isPresent();
        int major = noMajor ? NO_JAVA_ASKED : askedJava(findings);
        boolean plainlyAsked =
                java.filter(f -> !f.unknown() && MAJOR.matcher(f.value()).matches()).isPresent();
        Pinned pinned = resolvePins(all, plainlyAsked ? major : 0);
        if (pinned == Pinned.NONE && !noMajor) {
            resolveJava(all, major);
        }
        if (find(findings, "package.maven").isPresent()) {
            resolveMaven(all);
        }
        if (find(findings, "graalvm.version").isPresent()
                && pinned != Pinned.GRAALVM
                && !noMajor
                && !javaContradicts(all)) {
            if (find(all, JAVA_LINES).isPresent()) {
                resolveGraalvm(all, major);
            } else {
                // Its java would stand first on the path, before the distribution's JDK.
                noted(
                        all,
                        f -> f.key().equals("graalvm.version"),
                        "GraalVM for Java " + major,
                        new ToolUnavailable("it is fetched only beside a JDK fetched too"),
                        true);
            }
        }
        find(findings, "shfmt.version")
                .filter(f -> !f.unknown())
                .ifPresent(asked -> resolveShfmt(all, asked));
        return List.copyOf(all);
    }

    private void resolveShfmt(List<Finding> all, Finding asked) {
        try {
            ToolDownload shfmt = shfmts.shfmt(asked.value());
            all.add(
                    line(
                            asked,
                            "run.4-shfmt",
                            ToolLines.installProgram("shfmt", shfmt),
                            "shfmt " + shfmt.version() + ", the version Spotless is pinned to"));
        } catch (ToolUnavailable e) {
            // A distribution's shfmt is another version, which Spotless refuses; an unknown holds
            // no value, so the version asked stays in the note.
            all.replaceAll(
                    f ->
                            f.key().equals("shfmt.version")
                                    ? new Finding(
                                            f.part(),
                                            f.key(),
                                            "",
                                            f.file(),
                                            f.line(),
                                            f.origin(),
                                            true,
                                            f.note()
                                                    + "; shfmt "
                                                    + asked.value()
                                                    + " was asked, and not found: "
                                                    + e.getMessage())
                                    : f);
        }
    }

    private static int askedJava(List<Finding> findings) {
        return find(findings, "java.version")
                .filter(f -> !f.unknown())
                .map(f -> Integer.parseInt(f.value()))
                .orElse(NO_JAVA_ASKED);
    }

    private enum Pinned {
        NONE,
        JDK,
        GRAALVM,
        UNSETTLED
    }

    // The checkout's own pin wins over any vendor's newest, when it builds the release asked.
    private static Pinned resolvePins(List<Finding> all, int floor) {
        List<Finding> pins =
                all.stream().filter(f -> f.key().startsWith(PIN) && !f.unknown()).toList();
        Map<Finding, JdkPin> fitting = new LinkedHashMap<>();
        for (Finding pin : pins) {
            String[] parts = pin.value().split(" ", 2);
            try {
                JdkPin read = JdkPin.of(parts[0], parts.length > 1 ? parts[1] : "");
                if (read.major() >= floor) {
                    fitting.put(pin, read);
                } else {
                    annotate(
                            all,
                            pin,
                            false,
                            "not taken: Java " + floor + " is asked, which it does not build");
                }
            } catch (ToolUnavailable e) {
                annotate(all, pin, true, "not taken: " + e.getMessage());
            }
        }
        if (fitting.isEmpty()) {
            return Pinned.NONE;
        }
        if (fitting.size() > 1) {
            fitting.keySet()
                    .forEach(
                            pin ->
                                    annotate(
                                            all,
                                            pin,
                                            true,
                                            "one of "
                                                    + fitting.size()
                                                    + " JDKs the poms pin, and which one the build"
                                                    + " takes is not read"));
            return Pinned.UNSETTLED;
        }
        Map.Entry<Finding, JdkPin> only = fitting.entrySet().iterator().next();
        Finding at = only.getKey();
        JdkPin pin = only.getValue();
        String where = at.note();
        all.removeIf(JDK_PACKAGES);
        all.add(line(at, JAVA_LINES, ToolLines.install("java", pin.download()), where));
        for (String name : List.of("JAVA_HOME", "JDK_HOME", "JRE_HOME")) {
            all.add(line(at, "env." + name, "/opt/java", where));
        }
        if (pin.vendor().equals("GraalVM")) {
            all.add(line(at, "env.GRAALVM_HOME", "/opt/java", where));
            return Pinned.GRAALVM;
        }
        return Pinned.JDK;
    }

    private static void annotate(List<Finding> all, Finding which, boolean unknown, String why) {
        all.replaceAll(
                f ->
                        f.equals(which)
                                ? new Finding(
                                        f.part(),
                                        f.key(),
                                        unknown ? "" : f.value(),
                                        f.file(),
                                        f.line(),
                                        f.origin(),
                                        unknown || f.unknown(),
                                        // An unknown holds no value, so the pin stays in the note.
                                        f.note()
                                                + (unknown ? " (" + f.value() + ")" : "")
                                                + "; "
                                                + why)
                                : f);
    }

    private void resolveJava(List<Finding> all, int major) {
        Predicate<Finding> packages = JDK_PACKAGES;
        Optional<Finding> anchor = all.stream().filter(packages).findFirst();
        if (anchor.isEmpty() || anchor.get().unknown() && javaContradicts(all)) {
            return;
        }
        try {
            ToolDownload jdk = jdks.temurin(major);
            all.removeIf(packages);
            Finding at = anchor.get();
            String where = "Temurin " + jdk.version() + " for Java " + major;
            all.add(line(at, JAVA_LINES, ToolLines.install("java", jdk), where));
            for (String name : List.of("JAVA_HOME", "JDK_HOME", "JRE_HOME")) {
                all.add(line(at, "env." + name, "/opt/java", where));
            }
        } catch (ToolUnavailable e) {
            noted(all, packages, "Java " + major, e, false);
        }
    }

    // Limits no JDK meets name no JDK to fetch: Detect's unknown stands.
    private static boolean javaContradicts(List<Finding> all) {
        return find(all, "java.version").filter(Finding::unknown).isPresent();
    }

    private void resolveMaven(List<Finding> all) {
        Finding asked = find(all, "maven.version").orElse(null);
        String range = asked == null || asked.unknown() ? "" : asked.value();
        Predicate<Finding> packages = f -> f.key().equals("package.maven");
        Optional<Mirror> mirror = mirrorOfCentral(all);
        try {
            ToolDownload maven =
                    mavens.maven(range, mirror.map(Mirror::url).orElse(MavenDownloads.CENTRAL));
            Finding at = find(all, "package.maven").orElseThrow();
            all.removeIf(packages);
            String where =
                    "Maven "
                            + maven.version()
                            + mirror.map(m -> ", through mirror " + m.id()).orElse("");
            all.add(line(at, "run.3-maven", ToolLines.install("maven", maven), where));
            all.add(line(at, "env.MAVEN_HOME", "/opt/maven", where));
        } catch (ToolUnavailable e) {
            noted(all, packages, ("Maven " + range).strip(), e, false);
        }
    }

    private record Mirror(String id, String url) {}

    // The mirror the person's Maven reaches Central through, as Maven picks it: one naming central
    // alone first, then the first pattern taking it in the settings' order. It is used only when
    // its
    // URL can stand in a shell line and be fetched; otherwise Maven comes from Central itself.
    private static Optional<Mirror> mirrorOfCentral(List<Finding> all) {
        List<Finding> mirrors =
                all.stream().filter(f -> f.key().startsWith(MIRROR_OF) && !f.unknown()).toList();
        Optional<Finding> taking =
                mirrors.stream()
                        .filter(f -> f.value().equals("central"))
                        .findFirst()
                        .or(
                                () ->
                                        mirrors.stream()
                                                .filter(f -> takesCentral(f.value()))
                                                .findFirst());
        return taking.flatMap(
                takes -> {
                    String id = takes.key().substring(MIRROR_OF.length());
                    return find(all, "repository." + id)
                            .filter(f -> !f.unknown())
                            .map(Finding::value)
                            .filter(ToolResolver::plainHttps)
                            .map(u -> new Mirror(id, u.endsWith("/") ? u : u + "/"));
                });
    }

    private static boolean takesCentral(String pattern) {
        List<String> parts = List.of(pattern.split(","));
        return !parts.contains("!central")
                && (parts.contains("*")
                        || parts.contains("external:*")
                        || parts.contains("central"));
    }

    private static boolean plainHttps(String url) {
        Matcher plain = PLAIN_HTTPS.matcher(url);
        if (!plain.matches()) {
            return false;
        }
        String port = plain.group(2);
        return port == null
                || Integer.parseInt(port) >= 1 && Integer.parseInt(port) <= HIGHEST_PORT;
    }

    private void resolveGraalvm(List<Finding> all, int major) {
        Finding at = find(all, "graalvm.version").orElseThrow();
        try {
            ToolDownload graalvm = jdks.graalvm(major);
            String where = "GraalVM " + graalvm.version() + " for Java " + major;
            all.add(line(at, "run.2-graalvm", ToolLines.installBeside("graalvm", graalvm), where));
            all.add(line(at, "env.GRAALVM_HOME", "/opt/graalvm", where));
        } catch (ToolUnavailable e) {
            // No package stands in for it, so it is left unknown.
            noted(
                    all,
                    f -> f.key().equals("graalvm.version"),
                    "GraalVM for Java " + major,
                    e,
                    true);
        }
    }

    private static void noted(
            List<Finding> all,
            Predicate<Finding> which,
            String asked,
            ToolUnavailable why,
            boolean unknown) {
        all.replaceAll(
                f ->
                        which.test(f)
                                ? new Finding(
                                        f.part(),
                                        f.key(),
                                        f.value(),
                                        f.file(),
                                        f.line(),
                                        f.origin(),
                                        unknown || f.unknown(),
                                        f.note()
                                                + "; "
                                                + asked
                                                + " was asked, and not found: "
                                                + why.getMessage())
                                : f);
    }

    private static Finding line(Finding at, String key, String value, String note) {
        return new Finding(
                Finding.Part.TOOLS, key, value, at.file(), at.line(), at.origin(), false, note);
    }

    private static Optional<Finding> find(List<Finding> findings, String key) {
        return findings.stream().filter(f -> f.key().equals(key)).findFirst();
    }
}
