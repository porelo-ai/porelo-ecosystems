package org.fuin.porelo.ecosystem.maven;

import java.util.List;

/**
 * What a checkout and the person's settings say about the hosts a build must reach.
 *
 * @param hosts every host, each once per place
 * @param unknowns what could not be answered
 * @param unplaced what was seen and could not be taken into account
 */
public record RepositoryHostsAnswer(
        List<RepositoryHost> hosts, List<UnknownValue> unknowns, List<Unplaced> unplaced) {

    /** Copies the lists. */
    public RepositoryHostsAnswer {
        hosts = List.copyOf(hosts);
        unknowns = List.copyOf(unknowns);
        unplaced = List.copyOf(unplaced);
    }
}
