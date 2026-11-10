package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jspecify.annotations.Nullable;

/**
 * What in a build picks or fetches a JDK of its own through a toolchain, which a task does not
 * have. It only warns: no JDK it asks is fetched and no toolchains.xml is written.
 */
final class ToolchainUse {
    private static final String FETCHING = "org.mvnsearch:toolchains-maven-plugin";
    private static final String MAVENS = "org.apache.maven.plugins:maven-toolchains-plugin";
    // The goal that asks for a toolchain; maven-toolchains-plugin's select-jdk-toolchain is unread.
    private static final String GOAL = "toolchain";
    private static final Map<String, String> NOTES =
            Map.of(
                    FETCHING,
                    " fetches %s from api.foojay.io and the vendor's download host when the"
                            + " toolchains.xml holds no match, which a task's does not; no host"
                            + " is opened for it, as nothing offline names the download host; to"
                            + " let it fetch, add api.foojay.io and that host in porelo.yml under"
                            + " egress yourself",
                    MAVENS,
                    " needs %s in a toolchains.xml, which a task does not have unless another"
                            + " plugin writes one, so the build stops at its toolchain goal");

    private final List<Unplaced> unplaced = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    /** Takes in one pom's effective model, whose pluginManagement the model read holds apart. */
    void read(Model model, Places places) {
        Build build = model.getBuild();
        if (build == null) {
            return;
        }
        for (Plugin plugin : build.getPlugins()) {
            String note = NOTES.get(plugin.getKey());
            if (note == null) {
                continue;
            }
            List<Plugin> managed =
                    build.getPluginManagement() == null
                            ? List.of()
                            : build.getPluginManagement().getPlugins().stream()
                                    .filter(other -> other.getKey().equals(plugin.getKey()))
                                    .toList();
            if (Stream.concat(Stream.of(plugin), managed.stream())
                    .noneMatch(ToolchainUse::runsTheGoal)) {
                continue;
            }
            // A JDK the plugin states itself wins over one its management states.
            List<Xpp3Dom> toolchains = toolchains(List.of(plugin));
            if (toolchains.isEmpty()) {
                toolchains = toolchains(managed);
            }
            add(
                    places.of(plugin.getLocation("")),
                    plugin.getArtifactId() + note.formatted(asked(toolchains)));
        }
    }

    /** What was seen, once for each pom and plugin. */
    List<Unplaced> unplaced() {
        return List.copyOf(unplaced);
    }

    private static boolean runsTheGoal(Plugin plugin) {
        return plugin.getExecutions().stream()
                .map(PluginExecution::getGoals)
                .anyMatch(goals -> goals.contains(GOAL));
    }

    // The plugin's own configuration, and each execution's that runs the goal, never another
    // goal's.
    private static List<Xpp3Dom> toolchains(List<Plugin> plugins) {
        return plugins.stream()
                .flatMap(
                        plugin ->
                                Stream.concat(
                                        Stream.of(plugin.getConfiguration()),
                                        plugin.getExecutions().stream()
                                                .filter(e -> e.getGoals().contains(GOAL))
                                                .map(PluginExecution::getConfiguration)))
                .filter(Xpp3Dom.class::isInstance)
                .map(Xpp3Dom.class::cast)
                .map(configuration -> configuration.getChild("toolchains"))
                .filter(toolchains -> toolchains != null)
                .toList();
    }

    // The JDK for the build, and the one for its tests where the plugin asks one apart, each once.
    private static String asked(List<Xpp3Dom> toolchains) {
        Set<String> jdks = new LinkedHashSet<>();
        for (Xpp3Dom toolchain : toolchains) {
            Xpp3Dom build = toolchain.getChild("jdk");
            if (build != null) {
                jdks.add(jdk(build, ""));
            }
            Xpp3Dom tests = toolchain.getChild("testJdk");
            if (tests != null) {
                jdks.add(jdk(tests, " for its tests"));
            }
        }
        return jdks.isEmpty() ? "a JDK no toolchain states" : String.join(" and ", jdks);
    }

    private static String jdk(Xpp3Dom jdk, String what) {
        String version = value(jdk, "version");
        if (version == null || version.contains("${")) {
            return "a JDK whose version is not read here ("
                    + (version == null ? "none stated" : version)
                    + ")"
                    + what;
        }
        String vendor = value(jdk, "vendor");
        String from =
                vendor == null
                        ? ""
                        : vendor.contains("${")
                                ? " (a vendor not read here, " + vendor + ")"
                                : " (" + vendor + ")";
        return "JDK " + version + from + what;
    }

    private static @Nullable String value(Xpp3Dom parent, String name) {
        Xpp3Dom child = parent.getChild(name);
        String value = child == null ? null : child.getValue();
        return value == null || value.isBlank() ? null : value.strip();
    }

    // One line per pom and plugin, however many executions ask.
    private void add(Place place, String note) {
        if (seen.add(place.file() + "\n" + note)) {
            unplaced.add(new Unplaced(note, place));
        }
    }
}
