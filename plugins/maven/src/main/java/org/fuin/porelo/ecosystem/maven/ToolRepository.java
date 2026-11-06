package org.fuin.porelo.ecosystem.maven;

import java.util.List;
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

    private ToolRepository() {}

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
