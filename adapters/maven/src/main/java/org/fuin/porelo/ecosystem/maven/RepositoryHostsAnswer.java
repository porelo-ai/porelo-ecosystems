package org.fuin.porelo.ecosystem.maven;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a checkout and the person's settings say about the hosts a build must reach.
 *
 * @param hosts every host, each once per place
 * @param unknowns what could not be answered
 * @param unplaced what was seen and could not be taken into account
 * @param firstUnread the first pom or parent that went unread, so what it says is not known; null
 *     when every one was read
 */
public record RepositoryHostsAnswer(
        List<RepositoryHost> hosts,
        List<UnknownValue> unknowns,
        List<Unplaced> unplaced,
        @Nullable UnknownValue firstUnread) {

    /** Copies the lists. */
    public RepositoryHostsAnswer {
        hosts = List.copyOf(hosts);
        unknowns = List.copyOf(unknowns);
        unplaced = List.copyOf(unplaced);
    }

    /** Whether every pom and parent was read. */
    public boolean everyPomRead() {
        return firstUnread == null;
    }
}
