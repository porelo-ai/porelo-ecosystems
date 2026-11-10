package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a checkout's {@code .mvn/maven.config} and {@code .mvn/jvm.config} tell its build: input to
 * the answer as they are to the build. What cannot be followed is named.
 */
final class MavenOptions {
    private static final String MAVEN_CONFIG = ".mvn/maven.config";
    private static final String JVM_CONFIG = ".mvn/jvm.config";
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Set<String> WITH_A_VALUE =
            Set.of(
                    "-D",
                    "--define",
                    "-P",
                    "--activate-profiles",
                    "-s",
                    "--settings",
                    "-gs",
                    "--global-settings");
    // These change how a build runs and reports, not what its model holds.
    private static final Pattern LEAVES_THE_MODEL_ALONE =
            Pattern.compile(
                    "-B|--batch-mode|-ntp|--no-transfer-progress|-q|--quiet|-X|--debug|-e|--errors"
                            + "|-U|--update-snapshots|-o|--offline|-V|--show-version|-T\\S*"
                            + "|--threads=\\S*|-fae|-ff|-fn|-C|-c");
    private static final Pattern LOADS_CODE =
            Pattern.compile("-javaagent:.*|-agentlib:.*|-agentpath:.*|-Xbootclasspath.*");

    private final Properties user = new Properties();
    private final Properties system = new Properties();
    private final List<String> activeProfiles = new ArrayList<>();
    private final List<String> inactiveProfiles = new ArrayList<>();
    private final List<Unplaced> notes = new ArrayList<>();
    private @Nullable String settings;
    private int settingsLine;
    private @Nullable String globalSettings;
    private int globalSettingsLine;

    private MavenOptions() {}

    static MavenOptions read(Path checkout) {
        MavenOptions options = new MavenOptions();
        options.mavenArguments(options.lines(checkout, MAVEN_CONFIG));
        List<String> jvm = options.lines(checkout, JVM_CONFIG);
        for (int i = 0; i < jvm.size(); i++) {
            options.jvmLine(jvm.get(i), i + 1);
        }
        return options;
    }

    /** The properties given to Maven itself, which come before a pom's. */
    Properties user() {
        return user;
    }

    /** The properties given to its JVM, which come after a pom's. */
    Properties system() {
        return system;
    }

    List<String> activeProfiles() {
        return activeProfiles;
    }

    List<String> inactiveProfiles() {
        return inactiveProfiles;
    }

    List<Unplaced> notes() {
        return notes;
    }

    /** The settings file named, as written; null when the build reads the usual one. */
    @Nullable String settings() {
        return settings;
    }

    /** Where the settings file is named. */
    Place settingsPlace() {
        return new Place(MAVEN_CONFIG, settingsLine, Origin.CHECKOUT);
    }

    /** The global settings file named, as written; null when none is. */
    @Nullable String globalSettings() {
        return globalSettings;
    }

    /** Where the global settings file is named. */
    Place globalSettingsPlace() {
        return new Place(MAVEN_CONFIG, globalSettingsLine, Origin.CHECKOUT);
    }

    /** A property as Maven's settings see it: what Maven was given comes first. */
    @Nullable String property(String name) {
        String value = user.getProperty(name);
        return value != null ? value : system.getProperty(name);
    }

    private List<String> lines(Path checkout, String name) {
        Path file = checkout.resolve(name);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length <= MAX_BYTES) {
                return new String(bytes, StandardCharsets.UTF_8).lines().toList();
            }
        } catch (IOException e) {
            notes.add(
                    note(
                            "this file cannot be read, and the build stops on it too; nothing in"
                                    + " porelo.yml changes that",
                            name,
                            0));
            return List.of();
        }
        notes.add(note("this file is too large to be followed" + OwnFile.HOST_OR_TOOL, name, 0));
        return List.of();
    }

    // Maven takes each line as one argument, so a space in it belongs to its value.
    private void mavenArguments(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String argument = lines.get(i).strip();
            if (argument.isEmpty() || argument.startsWith("#")) {
                continue;
            }
            boolean separate = WITH_A_VALUE.contains(argument) && i + 1 < lines.size();
            String value = separate ? lines.get(++i).strip() : "";
            if (argument.equals("-D") || argument.equals("--define")) {
                define(user, value);
            } else if (argument.equals("-P") || argument.equals("--activate-profiles")) {
                profiles(value);
            } else if (argument.equals("-s") || argument.equals("--settings")) {
                settings(value, i + 1);
            } else if (argument.equals("-gs") || argument.equals("--global-settings")) {
                globalSettings(value, i + 1);
            } else if (argument.startsWith("--global-settings=")) {
                globalSettings(argument.substring("--global-settings=".length()), i + 1);
            } else if (argument.startsWith("-gs")) {
                globalSettings(lines.get(i).stripLeading().substring(3), i + 1);
            } else if (argument.startsWith("--settings=")) {
                settings(argument.substring("--settings=".length()), i + 1);
            } else if (argument.startsWith("-s") && !argument.startsWith("-s=")) {
                // The rest of the argument is the value, a space in front of it too.
                settings(lines.get(i).stripLeading().substring(2), i + 1);
            } else if (argument.startsWith("-D")) {
                define(user, argument.substring(2));
            } else if (argument.startsWith("-P")) {
                profiles(argument.substring(2));
            } else if (argument.startsWith("-")
                    && !LEAVES_THE_MODEL_ALONE.matcher(argument).matches()) {
                notes.add(
                        note(
                                "the option "
                                        + argument
                                        + " is not followed"
                                        + OwnFile.HOST_OR_TOOL,
                                MAVEN_CONFIG,
                                i + 1));
            }
        }
    }

    private void globalSettings(String file, int line) {
        globalSettings = file;
        globalSettingsLine = line;
    }

    private void settings(String file, int line) {
        settings = file;
        settingsLine = line;
    }

    private void jvmLine(String line, int number) {
        for (String token : tokens(line)) {
            if (token.startsWith("-D")) {
                define(system, token.substring(2));
            } else if (LOADS_CODE.matcher(token).matches()) {
                notes.add(
                        note(
                                "the option "
                                        + token
                                        + " loads code that can change the model, and is not"
                                        + " followed"
                                        + OwnFile.HOST_OR_TOOL,
                                JVM_CONFIG,
                                number));
            }
        }
    }

    private static String[] tokens(String line) {
        String stripped = line.strip();
        return stripped.isEmpty() || stripped.startsWith("#")
                ? new String[0]
                : stripped.split("\\s+");
    }

    private static void define(Properties into, String definition) {
        int equals = definition.indexOf('=');
        if (equals < 0) {
            into.setProperty(definition, "true");
        } else {
            into.setProperty(definition.substring(0, equals), definition.substring(equals + 1));
        }
    }

    private void profiles(String list) {
        for (String profile : list.split(",")) {
            if (profile.startsWith("!") || profile.startsWith("-")) {
                inactiveProfiles.add(profile.substring(1));
            } else if (!profile.isEmpty()) {
                activeProfiles.add(profile.startsWith("+") ? profile.substring(1) : profile);
            }
        }
    }

    private static Unplaced note(String note, String file, int line) {
        return new Unplaced(note, new Place(file, line, Origin.CHECKOUT));
    }
}
