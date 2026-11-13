package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fuin.porelo.ecosystem.api.Finding;

/**
 * A settings file inside the checkout that the checkout's own build uses: named by {@code -s} in
 * {@code .mvn/maven.config}, or passed with {@code -s} to Maven in a workflow of the checkout. The
 * task's Maven then reads it alone, and no settings file is generated for the task.
 */
final class OwnSettings {
    static final String KEY = "extension.ownSettings";
    private static final String WORKFLOWS = ".github/workflows";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_WORKFLOWS = 64;
    private static final Pattern MAVEN_WITH_SETTINGS =
            Pattern.compile(
                    "(?:^|[\\s;&|(])(?:\\./)?(?:mvnw|mvn)(?=\\s).*?\\s(?:-s|--settings)(?:\\s+|=)"
                            + "['\"]?([^\\s'\"`;&|]+)");
    // A shell or YAML comment starts at a '#' with a space or nothing before it.
    private static final Pattern COMMENT = Pattern.compile("(?:^|\\s)#.*");
    private static final String NOTE =
            "the build uses the settings file checked in here, so Porelo generates none for the"
                    + " task";

    private OwnSettings() {}

    /**
     * The checked-in settings file the build uses, as a finding Porelo passes back to {@code
     * Configure} in the {@code extensions.maven} block.
     *
     * @param checkout the checkout, absolute
     * @param options what {@code .mvn/maven.config} says
     * @return the finding; empty when the build uses no settings file of the checkout
     */
    static Optional<Finding> of(Path checkout, MavenOptions options) {
        String named = options.settings();
        if (named != null) {
            // Maven reads only the file the config names; a workflow's own -s does not apply then.
            return inside(checkout, named)
                    .map(path -> Answers.rule(KEY, path, options.settingsPlace(), NOTE));
        }
        for (Path workflow : workflows(checkout)) {
            List<String> lines = lines(workflow);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m =
                        MAVEN_WITH_SETTINGS.matcher(COMMENT.matcher(lines.get(i)).replaceFirst(""));
                if (m.find()) {
                    Optional<String> path = inside(checkout, m.group(1));
                    if (path.isPresent()) {
                        String file = checkout.relativize(workflow).toString();
                        return Optional.of(
                                Answers.rule(
                                        KEY,
                                        path.get(),
                                        new Place(file, i + 1, Origin.CHECKOUT),
                                        NOTE));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A path as the extension block may hold it: relative, inside the checkout, with no step out.
     *
     * @param path the path as written
     * @return whether it stays inside the checkout
     */
    static boolean staysInside(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains("\0")) {
            return false;
        }
        Path relative = Path.of(path).normalize();
        return !relative.toString().isEmpty() && !relative.startsWith("..");
    }

    private static Optional<String> inside(Path checkout, String written) {
        if (!staysInside(written)) {
            return Optional.empty();
        }
        Path file = checkout.resolve(written).normalize();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            // A link may lead out of the checkout, and the file it leads to is not the checkout's.
            Path realCheckout = checkout.toRealPath();
            if (!file.toRealPath().startsWith(realCheckout)) {
                return Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.of(checkout.relativize(file).toString());
    }

    private static List<Path> workflows(Path checkout) {
        Path directory = checkout.resolve(WORKFLOWS);
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return found;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.{yml,yaml}")) {
            for (Path file : files) {
                if (Files.isRegularFile(file)) {
                    found.add(file);
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        found.sort(null);
        return found.size() > MAX_WORKFLOWS ? found.subList(0, MAX_WORKFLOWS) : found;
    }

    private static List<String> lines(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            return bytes.length > MAX_BYTES
                    ? List.of()
                    : new String(bytes, StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
