package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;

/** What in a build starts containers, which a task does not support. */
final class ContainerUse {
    private static final String TESTCONTAINERS = "org.testcontainers";
    private static final Set<String> PLUGINS =
            Set.of(
                    "io.fabric8:docker-maven-plugin",
                    "com.spotify:dockerfile-maven-plugin",
                    "org.eclipse.jkube:kubernetes-maven-plugin",
                    "org.eclipse.jkube:openshift-maven-plugin",
                    "io.fabric8:fabric8-maven-plugin");

    private final List<Unplaced> unplaced = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    /** Takes in one pom's effective model, whose inherited parts stand in their own pom. */
    void read(Model model, Places places) {
        for (Dependency dependency : model.getDependencies()) {
            if (TESTCONTAINERS.equals(dependency.getGroupId())
                    && !"pom".equals(dependency.getType())) {
                add(
                        places.of(dependency.getLocation("")),
                        "this build uses Testcontainers, whose tests start containers, which a task"
                                + " does not support in any security class, so those tests fail or"
                                + " skip themselves; nothing in porelo.yml changes that;"
                                + " -DskipTests in the build's command leaves them out");
            }
        }
        if (model.getBuild() != null) {
            for (Plugin plugin : model.getBuild().getPlugins()) {
                if (PLUGINS.contains(plugin.getKey())) {
                    add(
                            places.of(plugin.getLocation("")),
                            plugin.getArtifactId()
                                    + " starts containers or needs their daemon, which a task does"
                                    + " not support in any security class, so its goals fail where"
                                    + " the build runs them; nothing in porelo.yml changes that;"
                                    + " what remains is a build command that leaves those goals"
                                    + " out");
                }
            }
        }
    }

    /** What was seen, once for each place it stands. */
    List<Unplaced> unplaced() {
        return List.copyOf(unplaced);
    }

    // One line per pom and statement, however many artifacts or lines stand there.
    private void add(Place place, String note) {
        if (seen.add(place.file() + "\n" + note)) {
            unplaced.add(new Unplaced(note, place));
        }
    }
}
