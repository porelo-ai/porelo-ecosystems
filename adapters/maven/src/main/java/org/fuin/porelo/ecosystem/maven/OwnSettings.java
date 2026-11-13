package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
    // A step's command: after the list's dash and the key.
    private static final Pattern RUN = Pattern.compile("\\s*(?:-\\s+)?run:\\s*(.*)");
    // Another key of YAML, whose value Maven never runs.
    private static final Pattern OTHER_KEY =
            Pattern.compile("\\s*(?:-\\s+)?[A-Za-z_][\\w.-]*:(?:\\s.*)?");
    // The indicator of a block, whose lines follow.
    private static final Pattern BLOCK = Pattern.compile("[|>][-+0-9]*");
    private static final String NOTE =
            "the build uses the settings file checked in here, so Porelo generates none for the"
                    + " task";
    private static final String NOT_FOLLOWED =
            ", so a settings file it may name is not followed and one is generated for the task";

    private OwnSettings() {}

    /**
     * What the checkout says of its own settings file.
     *
     * @param finding the checked-in settings file the build uses, empty when it uses none
     * @param notes the workflows not followed, and the settings files named beside the one taken
     */
    record Answer(Optional<Finding> finding, List<Unplaced> notes) {}

    /**
     * The checked-in settings file the build uses, as a finding Porelo passes back to {@code
     * Configure} in the {@code extensions.maven} block.
     *
     * @param checkout the checkout, absolute
     * @param options what {@code .mvn/maven.config} says
     * @return the finding, and what was not followed
     */
    static Answer of(Path checkout, MavenOptions options) {
        String named = options.settings();
        if (named != null) {
            // Maven reads only the file the config names; a workflow's own -s does not apply then.
            return new Answer(
                    inside(checkout, named)
                            .map(path -> Answers.rule(KEY, path, options.settingsPlace(), NOTE)),
                    List.of());
        }
        List<Unplaced> notes = new ArrayList<>();
        Finding taken = null;
        Set<String> beside = new HashSet<>();
        for (Path workflow : workflows(checkout, notes)) {
            String file = checkout.relativize(workflow).toString();
            Optional<List<String>> lines = lines(workflow);
            if (lines.isEmpty()) {
                notes.add(
                        note("this workflow is too large or cannot be read" + NOT_FOLLOWED, file));
                continue;
            }
            for (Line line : logical(lines.get())) {
                Optional<String> path =
                        ShellCommand.settingsFile(line.text()).flatMap(p -> inside(checkout, p));
                if (path.isEmpty()) {
                    continue;
                }
                Place place = new Place(file, line.number(), Origin.CHECKOUT);
                if (taken == null) {
                    taken = Answers.rule(KEY, path.get(), place, NOTE);
                } else if (!taken.value().equals(path.get()) && beside.add(path.get())) {
                    notes.add(
                            new Unplaced(
                                    "the build here passes "
                                            + path.get()
                                            + " to Maven, beside "
                                            + taken.value()
                                            + " at "
                                            + taken.file()
                                            + ":"
                                            + taken.line()
                                            + ", which is the one taken",
                                    place));
                }
            }
        }
        return new Answer(Optional.ofNullable(taken), List.copyOf(notes));
    }

    private record Line(String text, int number) {}

    // Each line without its comment and YAML's step markers, a line ending in '\' joined with
    // the next as the shell joins them, at the number of its first line. A line of another key
    // is left out.
    private static List<Line> logical(List<String> lines) {
        List<Line> logical = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int first = 0;
        for (int i = 0; i < lines.size(); i++) {
            String text = ShellCommand.withoutComment(lines.get(i));
            if (current.isEmpty()) {
                first = i + 1;
                Matcher run = RUN.matcher(text);
                if (run.matches()) {
                    text = scalar(run.group(1).strip());
                } else if (OTHER_KEY.matcher(text).matches()) {
                    continue;
                }
            }
            if (text.endsWith("\\")) {
                current.append(text, 0, text.length() - 1).append(' ');
                continue;
            }
            current.append(text);
            logical.add(new Line(current.toString(), first));
            current.setLength(0);
        }
        if (!current.isEmpty()) {
            logical.add(new Line(current.toString(), first));
        }
        return logical;
    }

    // A run value as YAML reads it: a block's indicator stands for no text, quotes are taken away.
    private static String scalar(String value) {
        if (BLOCK.matcher(value).matches()) {
            return "";
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            String inner = value.substring(1, value.length() - 1);
            StringBuilder read = new StringBuilder();
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '\\' && i + 1 < inner.length()) {
                    c = inner.charAt(++i);
                }
                read.append(c);
            }
            return read.toString();
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
    }

    private static Unplaced note(String why, String file) {
        return new Unplaced(why, new Place(file, 0, Origin.CHECKOUT));
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

    private static List<Path> workflows(Path checkout, List<Unplaced> notes) {
        Path directory = checkout.resolve(WORKFLOWS);
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return found;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    found.add(file);
                }
            }
        } catch (IOException e) {
            notes.add(note("this directory cannot be read" + NOT_FOLLOWED, WORKFLOWS));
            return List.of();
        }
        found.sort(null);
        List<Path> read = new ArrayList<>();
        for (Path file : found) {
            String name = checkout.relativize(file).toString();
            if (read.size() == MAX_WORKFLOWS) {
                notes.add(
                        note(
                                "this workflow is past the "
                                        + MAX_WORKFLOWS
                                        + " read"
                                        + NOT_FOLLOWED,
                                name));
            } else if (!ownFile(checkout, file)) {
                notes.add(
                        note(
                                "this workflow lies outside the checkout, by a link" + NOT_FOLLOWED,
                                name));
            } else {
                read.add(file);
            }
        }
        return read;
    }

    // A regular file whose real place is inside the checkout's.
    private static boolean ownFile(Path checkout, Path file) {
        try {
            return Files.isRegularFile(file) && file.toRealPath().startsWith(checkout.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static Optional<List<String>> lines(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            return bytes.length > MAX_BYTES
                    ? Optional.empty()
                    : Optional.of(new String(bytes, StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
