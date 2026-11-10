package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.fuin.porelo.ecosystem.api.Finding;

/**
 * A repository as Porelo's draft declares it for a task, by the name its settings give it, with
 * what a mirror takes as the Maven extension; only what Porelo can carry is answered.
 */
final class ToolRepository {
    // A Porelo repository's name: a dot would split the extension key that names it.
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern MIRROR_OF = Pattern.compile("[A-Za-z0-9_.:*!,-]+");

    private static final Set<RepositoryHost.Kind> DECLARED =
            Set.of(
                    RepositoryHost.Kind.REPOSITORY,
                    RepositoryHost.Kind.PLUGIN_REPOSITORY,
                    RepositoryHost.Kind.MIRROR);

    private static final Set<String> LAYOUTS_A_TASK_KEEPS = Set.of("", "*", "default,legacy");

    private ToolRepository() {}

    /**
     * The findings that declare every repository the build reaches, once per id: the repositories
     * as the build meets them, then the mirrors in the order of the settings, which Maven tries
     * patterns in.
     *
     * @param hosts every host the build reaches
     * @return the findings; an id under two addresses is unknown and not declared
     */
    static List<Finding> of(List<RepositoryHost> hosts) {
        Map<String, List<RepositoryHost>> byId = new LinkedHashMap<>();
        hosts.stream()
                .filter(h -> DECLARED.contains(h.kind()))
                .forEach(h -> byId.computeIfAbsent(h.id(), k -> new ArrayList<>()).add(h));
        List<RepositoryHost> chosen = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        byId.forEach(
                (id, same) -> {
                    if (same.stream().map(RepositoryHost::url).distinct().count() > 1) {
                        findings.add(
                                unknown(
                                        "unknown.repository." + id,
                                        same.get(0),
                                        "the id "
                                                + id
                                                + " names more than one address, and a Porelo"
                                                + " repository's name stands for one; it is not"
                                                + " declared for the task"));
                    } else {
                        chosen.add(
                                same.stream()
                                        .filter(h -> h.kind() == RepositoryHost.Kind.MIRROR)
                                        .findFirst()
                                        .orElse(same.get(0)));
                    }
                });
        chosen.stream()
                .sorted(Comparator.comparingInt(RepositoryHost::rank))
                .forEach(h -> findings.addAll(of(h)));
        return findings;
    }

    /**
     * The findings that declare a host's repository, or why it cannot be declared.
     *
     * @param host a repository, plugin repository or mirror the build reaches
     * @return its findings; none for a host of another kind
     */
    static List<Finding> of(RepositoryHost host) {
        if (host.kind() != RepositoryHost.Kind.REPOSITORY
                && host.kind() != RepositoryHost.Kind.PLUGIN_REPOSITORY
                && host.kind() != RepositoryHost.Kind.MIRROR) {
            return List.of();
        }
        String id = host.id();
        if (!NAME.matcher(id).matches()) {
            return List.of(
                    unknown(
                            "unknown.repository." + id,
                            host,
                            "its id holds more than letters, digits, '_' and '-', or is longer"
                                    + " than 64, which a Porelo repository's name cannot; it is not"
                                    + " declared for the task"));
        }
        Finding repository =
                Answers.rule(
                        "repository." + id,
                        host.url(),
                        host.place(),
                        "the repository " + id + " a task's Maven reaches through the broker");
        // Maven splits it at commas and trims no part, so a space changes what it takes.
        String mirrorOf = host.mirrorOf().strip();
        if (mirrorOf.isEmpty()) {
            return List.of(repository);
        }
        // A task's settings write no layouts, so Maven's default applies there.
        if (!LAYOUTS_A_TASK_KEEPS.contains(host.mirrorOfLayouts().strip())) {
            return List.of(
                    repository,
                    unknown(
                            "unknown.mirrorOf." + id,
                            host,
                            "mirror "
                                    + id
                                    + " takes only some layouts, which a task's settings do not"
                                    + " say, so the task sends nothing past it"));
        }
        if (!MIRROR_OF.matcher(mirrorOf).matches()) {
            return List.of(
                    repository,
                    unknown(
                            "unknown.mirrorOf." + id,
                            host,
                            "what mirror "
                                    + id
                                    + " takes holds more than Maven's ids and patterns, so the task"
                                    + " sends nothing past it"));
        }
        return List.of(
                repository,
                Answers.rule(
                        "extension.mirrorOf." + id,
                        mirrorOf,
                        host.place(),
                        "what mirror " + id + " takes, so the task's settings send it there too"));
    }

    private static Finding unknown(String key, RepositoryHost host, String why) {
        return Answers.unknown(Finding.Part.RULES, key, host.place(), why);
    }
}
