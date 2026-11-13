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
import java.util.Optional;
import java.util.Set;
import org.fuin.porelo.ecosystem.api.Finding;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * A settings file inside the checkout that the checkout's own build uses: named by {@code -s} in
 * {@code .mvn/maven.config}, or passed with {@code -s} to Maven in a step of a workflow of the
 * checkout, read from the directory the step runs in. The task's Maven then reads it alone, and no
 * settings file is generated for the task.
 */
final class OwnSettings {
    static final String KEY = "extension.ownSettings";
    private static final String WORKFLOWS = ".github/workflows";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_WORKFLOWS = 64;
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
            // As GitHub reads it: a job's default replaces the workflow's, a step's own replaces
            // both, and each is relative to the checkout's root.
            @Nullable Node workflowDefault = defaultDirectory(root);
            Set<Node> read = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Node job : values(get(root, "jobs"))) {
                @Nullable Node jobDefault = defaultDirectory(job);
                if (!(get(job, "steps") instanceof SequenceNode steps)) {
                    continue;
                }
                for (Node step : steps.getValue()) {
                    // A script an alias names again is the same script, read once.
                    if (get(step, "run") instanceof ScalarNode run && read.add(run)) {
                        @Nullable Node own = get(step, "working-directory");
                        @Nullable Node written =
                                own != null
                                        ? own
                                        : jobDefault != null ? jobDefault : workflowDefault;
                        step(file, run, ShellCommand.directory(scalar(written), ""));
                    }
                }
            }
        }

        // The lines of a step's script, from the directory it runs in. Only a literal block keeps
        // the lines of the file; any other value is placed at its first line.
        private void step(String file, ScalarNode run, @Nullable String directory) {
            int start = run.getStartMark().getLine() + 1;
            boolean literal = run.getScalarStyle() == ScalarStyle.LITERAL;
            int block = run.getScalarStyle() == ScalarStyle.FOLDED ? start + 1 : start;
            ShellCommand shell = new ShellCommand(directory);
            String[] lines = run.getValue().split("\n", -1);
            StringBuilder current = new StringBuilder();
            int first = 0;
            for (int i = 0; i < lines.length; i++) {
                String text = ShellCommand.withoutComment(lines[i]);
                if (current.isEmpty()) {
                    first = i;
                }
                // A line ending in '\' is joined with the next, as the shell joins them.
                if (text.endsWith("\\") && i + 1 < lines.length) {
                    current.append(text, 0, text.length() - 1).append(' ');
                    continue;
                }
                current.append(text);
                int number = literal ? start + 1 + first : block;
                Place place = new Place(file, number, Origin.CHECKOUT);
                Optional<String> path =
                        shell.settingsFile(current.toString()).flatMap(p -> inside(checkout, p));
                current.setLength(0);
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
