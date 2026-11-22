package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.fuin.porelo.ecosystem.api.Finding;
import org.fuin.porelo.ecosystem.shared.Answers;
import org.fuin.porelo.ecosystem.shared.Origin;
import org.fuin.porelo.ecosystem.shared.Place;
import org.fuin.porelo.ecosystem.shared.Unplaced;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * A settings file inside the checkout that the checkout's own build uses: named by {@code -s} in
 * {@code .mvn/maven.config}, or passed with {@code -s} to Maven in a step of a workflow of the
 * checkout, read with the step's shell from the directory it runs in and the path the checkout lies
 * at. The task's Maven then reads it alone, and no settings file is generated for the task.
 */
final class OwnSettings {
    static final String KEY = "extension.ownSettings";
    private static final String WORKFLOWS = ".github/workflows";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_WORKFLOWS = 64;
    private static final Pattern OWN_REPOSITORY =
            Pattern.compile("\\$\\{\\{\\s*github\\.repository\\s*\\}\\}");
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
        Reading reading = new Reading(checkout);
        List<Path> workflows = workflows(checkout, reading.notes);
        for (int index = 0; index < workflows.size(); index++) {
            Path workflow = workflows.get(index);
            String file = checkout.relativize(workflow).toString();
            if (index >= MAX_WORKFLOWS) {
                reading.notes.add(
                        note(
                                "this workflow is past the "
                                        + MAX_WORKFLOWS
                                        + " read"
                                        + NOT_FOLLOWED,
                                file));
            } else if (!ownFile(checkout, workflow)) {
                reading.notes.add(
                        note(
                                "this workflow lies outside the checkout, by a link" + NOT_FOLLOWED,
                                file));
            } else {
                reading.workflow(file, workflow);
            }
        }
        return new Answer(Optional.ofNullable(reading.taken), List.copyOf(reading.notes));
    }

    // What the workflows say, gathered in the order they are read.
    private static final class Reading {
        private final Path checkout;
        private final List<Unplaced> notes = new ArrayList<>();
        private final Set<String> beside = new HashSet<>();
        private @Nullable Finding taken;

        Reading(Path checkout) {
            this.checkout = checkout;
        }

        void workflow(String file, Path workflow) {
            Optional<String> text = text(workflow);
            if (text.isEmpty()) {
                notes.add(
                        note("this workflow is too large or cannot be read" + NOT_FOLLOWED, file));
                return;
            }
            Node root;
            try {
                root =
                        new Yaml(new SafeConstructor(options()))
                                .compose(new StringReader(text.get()));
            } catch (YAMLException e) {
                notes.add(note("this workflow is no YAML GitHub reads" + NOT_FOLLOWED, file));
                return;
            }
            WorkflowText source = new WorkflowText(text.get());
            // As GitHub reads it: a job's default replaces the workflow's, a step's own replaces
            // both, and each is relative to where the job's steps start.
            @Nullable Node workflowDefault = defaultDirectory(root);
            @Nullable Node workflowShell = defaultShell(root);
            Set<Node> read = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Node job : values(get(root, "jobs"))) {
                @Nullable Node jobDefault = defaultDirectory(job);
                @Nullable Node jobShell = defaultShell(job);
                if (!(get(job, "steps") instanceof SequenceNode steps)) {
                    continue;
                }
                // Where the checkout lies under where the steps start; null where not known.
                @Nullable String checkoutAt = "";
                for (Node step : steps.getValue()) {
                    if (ownCheckout(step)) {
                        checkoutAt =
                                ShellCommand.directory(scalar(get(get(step, "with"), "path")), "");
                    }
                    // A script an alias names again is the same script, read once.
                    if (get(step, "run") instanceof ScalarNode run && read.add(run)) {
                        @Nullable Node own = get(step, "working-directory");
                        @Nullable Node written =
                                own != null
                                        ? own
                                        : jobDefault != null ? jobDefault : workflowDefault;
                        @Nullable Node shell = first(get(step, "shell"), jobShell, workflowShell);
                        Optional<ShellCommand.Shell> kind =
                                ShellCommand.Shell.named(
                                        shell == null
                                                ? null
                                                : Objects.requireNonNullElse(scalar(shell), ""));
                        if (kind.isPresent()) {
                            ShellCommand reader =
                                    new ShellCommand(
                                            ShellCommand.directory(scalar(written), ""),
                                            kind.get());
                            step(file, source, run, reader, checkoutAt);
                        }
                    }
                }
            }
        }

        // The lines of a step's script, each file placed at the line of the word that names it.
        private void step(
                String file,
                WorkflowText source,
                ScalarNode run,
                ShellCommand shell,
                @Nullable String checkoutAt) {
            int[] at = source.lines(run);
            String[] lines = run.getValue().split("\n", -1);
            int[] starts = new int[lines.length];
            for (int k = 1; k < lines.length; k++) {
                starts[k] = starts[k - 1] + lines[k - 1].length() + 1;
            }
            StringBuilder current = new StringBuilder();
            // Where each line joined into the current one starts, in it and in the value.
            List<int[]> joined = new ArrayList<>();
            for (int i = 0; i < lines.length; i++) {
                String text = shell.withoutComment(lines[i]);
                joined.add(new int[] {current.length(), starts[i]});
                // A line ending in the shell's escape is joined with the next, as the shell does.
                if (shell.continues(text) && i + 1 < lines.length) {
                    current.append(text, 0, text.length() - 1).append(' ');
                    continue;
                }
                current.append(text);
                Place place = new Place(file, at[joined.get(0)[1]], Origin.CHECKOUT);
                Optional<ShellCommand.Named> named = shell.settingsFile(current.toString());
                Optional<String> path =
                        named.flatMap(n -> inCheckout(n.file(), checkoutAt))
                                .flatMap(p -> inside(checkout, p));
                if (named.isPresent()) {
                    place = new Place(file, at[offset(joined, named.get().at())], Origin.CHECKOUT);
                }
                current.setLength(0);
                joined.clear();
                // A heredoc's lines are its input, no commands.
                for (ShellCommand.Heredoc heredoc : shell.heredocs()) {
                    while (++i < lines.length && !heredoc.endsAt(lines[i])) {
                        // the heredoc's input
                    }
                }
                if (shell.tooDeep()) {
                    notes.add(
                            new Unplaced(
                                    "a command here is nested too deep to read" + NOT_FOLLOWED,
                                    place));
                }
                if (path.isEmpty()) {
                    continue;
                }
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
    }

    private static LoaderOptions options() {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(50);
        options.setAllowRecursiveKeys(false);
        return options;
    }

    // The working directory under defaults.run.
    private static @Nullable Node defaultDirectory(@Nullable Node node) {
        return get(get(get(node, "defaults"), "run"), "working-directory");
    }

    // The shell under defaults.run.
    private static @Nullable Node defaultShell(@Nullable Node node) {
        return get(get(get(node, "defaults"), "run"), "shell");
    }

    private static @Nullable Node first(@Nullable Node... nodes) {
        for (@Nullable Node node : nodes) {
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    // A step that checks out the workflow's own repository, which moves where its files lie.
    private static boolean ownCheckout(Node step) {
        @Nullable String uses = scalar(get(step, "uses"));
        if (uses == null || !uses.toLowerCase(Locale.ROOT).startsWith("actions/checkout@")) {
            return false;
        }
        @Nullable Node repository = get(get(step, "with"), "repository");
        return repository == null
                || OWN_REPOSITORY
                        .matcher(Objects.requireNonNullElse(scalar(repository), ""))
                        .matches();
    }

    // A file named from where the steps start, as a path of the checkout lying at checkoutAt.
    private static Optional<String> inCheckout(String file, @Nullable String checkoutAt) {
        if (checkoutAt == null || !staysInside(file)) {
            return Optional.empty();
        }
        String normal = Path.of(file).normalize().toString();
        if (checkoutAt.isEmpty()) {
            return Optional.of(normal);
        }
        return normal.startsWith(checkoutAt + "/")
                ? Optional.of(normal.substring(checkoutAt.length() + 1))
                : Optional.empty();
    }

    // Where in the value a place in a logical line stands, by the lines joined into it.
    private static int offset(List<int[]> joined, int at) {
        int[] line = joined.get(0);
        for (int[] each : joined) {
            if (each[0] <= at) {
                line = each;
            }
        }
        return line[1] + Math.max(0, at - line[0]);
    }

    // A workflow's text, to place a character of a value at the line it stands on.
    private static final class WorkflowText {
        private final String text;
        private final int[] lineStarts;

        WorkflowText(String text) {
            this.text = text;
            List<Integer> starts = new ArrayList<>(List.of(0));
            for (int i = text.indexOf('\n'); i >= 0; i = text.indexOf('\n', i + 1)) {
                starts.add(i + 1);
            }
            lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        }

        // The line, from 1, of each character of the value and of its end. The value's
        // characters stand in the text in order; what lies between them is indentation, a line
        // break folded away, a quote or the rest of an escape.
        int[] lines(ScalarNode node) {
            String value = node.getValue();
            int[] lines = new int[value.length() + 1];
            int line = node.getStartMark().getLine();
            int at = offset(node.getStartMark());
            int end = Math.max(at, offset(node.getEndMark()));
            ScalarStyle style = node.getScalarStyle();
            if (style == ScalarStyle.LITERAL || style == ScalarStyle.FOLDED) {
                int header = text.indexOf('\n', at);
                at = header < 0 ? end : header + 1;
                line++;
            } else if (style == ScalarStyle.SINGLE_QUOTED || style == ScalarStyle.DOUBLE_QUOTED) {
                at++;
            }
            for (int v = 0; v < value.length(); v++) {
                char c = value.charAt(v);
                lines[v] = line + 1;
                while (at < end) {
                    char s = text.charAt(at);
                    lines[v] = line + 1;
                    if (style == ScalarStyle.DOUBLE_QUOTED && s == '\\' && at + 1 < end) {
                        char escaped = text.charAt(at + 1);
                        at += escaped == 'x' ? 4 : escaped == 'u' ? 6 : escaped == 'U' ? 10 : 2;
                        if (escaped != '\n') {
                            break;
                        }
                        // An escaped line break stands for nothing in the value.
                        line++;
                        continue;
                    }
                    line += s == '\n' ? 1 : 0;
                    at++;
                    if (s == c || (Character.isWhitespace(c) && Character.isWhitespace(s))) {
                        break;
                    }
                }
            }
            lines[value.length()] = line + 1;
            return lines;
        }

        private int offset(Mark mark) {
            int line = Math.min(mark.getLine(), lineStarts.length - 1);
            int start = lineStarts[line];
            int next = line + 1 < lineStarts.length ? lineStarts[line + 1] : text.length();
            int columns = Math.min(mark.getColumn(), text.codePointCount(start, next));
            return Math.min(text.offsetByCodePoints(start, columns), text.length());
        }
    }

    private static @Nullable Node get(@Nullable Node node, String key) {
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                if (tuple.getKeyNode() instanceof ScalarNode name && name.getValue().equals(key)) {
                    return tuple.getValueNode();
                }
            }
        }
        return null;
    }

    private static List<Node> values(@Nullable Node node) {
        List<Node> values = new ArrayList<>();
        if (node instanceof MappingNode mapping) {
            mapping.getValue().forEach(tuple -> values.add(tuple.getValueNode()));
        }
        return values;
    }

    private static @Nullable String scalar(@Nullable Node node) {
        return node instanceof ScalarNode value ? value.getValue() : null;
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

    // The workflow files in name order: files and links, never a directory named like one.
    private static List<Path> workflows(Path checkout, List<Unplaced> notes) {
        Path directory = checkout.resolve(WORKFLOWS);
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return found;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                boolean workflow =
                        Files.isSymbolicLink(file)
                                || Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
                if (workflow && (name.endsWith(".yml") || name.endsWith(".yaml"))) {
                    found.add(file);
                }
            }
        } catch (IOException e) {
            notes.add(note("this directory cannot be read" + NOT_FOLLOWED, WORKFLOWS));
            return List.of();
        }
        found.sort(null);
        return found;
    }

    // A regular file whose real place is inside the checkout's.
    private static boolean ownFile(Path checkout, Path file) {
        try {
            return Files.isRegularFile(file) && file.toRealPath().startsWith(checkout.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static Optional<String> text(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            return bytes.length > MAX_BYTES
                    ? Optional.empty()
                    : Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
