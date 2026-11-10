package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.ecosystem.api.Finding;

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

    private final ToolDownloads tools;

    ToolResolver(ToolDownloads tools) {
        this.tools = tools;
    }

    /** Answers what replaces {@code findings}. */
    List<Finding> resolve(List<Finding> findings) {
        List<Finding> all = new ArrayList<>(findings);
        Optional<Finding> java = find(findings, "java.version");
        boolean noMajor =
                java.filter(f -> !f.unknown() && !MAJOR.matcher(f.value()).matches()).isPresent();
        int major = noMajor ? NO_JAVA_ASKED : askedJava(findings);
        if (!noMajor) {
            resolveJava(all, major);
        }
        if (find(findings, "package.maven").isPresent()) {
            resolveMaven(all);
        }
        if (find(findings, "graalvm.version").isPresent() && !noMajor && !javaContradicts(all)) {
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
        return List.copyOf(all);
    }

    private static int askedJava(List<Finding> findings) {
        return find(findings, "java.version")
                .filter(f -> !f.unknown())
                .map(f -> Integer.parseInt(f.value()))
                .orElse(NO_JAVA_ASKED);
    }

    private void resolveJava(List<Finding> all, int major) {
        Predicate<Finding> packages =
                f ->
                        f.key().equals("package.default-jdk-headless")
                                || (f.key().startsWith("package.openjdk-")
                                        && f.key().endsWith("-jdk-headless"));
        Optional<Finding> anchor = all.stream().filter(packages).findFirst();
        if (anchor.isEmpty() || anchor.get().unknown() && javaContradicts(all)) {
            return;
        }
        try {
            ToolDownload jdk = tools.temurin(major);
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
                    tools.maven(range, mirror.map(Mirror::url).orElse(ToolDownloads.CENTRAL));
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
            ToolDownload graalvm = tools.graalvm(major);
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
