package org.fuin.porelo.ecosystem.maven;

import java.util.List;
import java.util.Map;
import org.apache.maven.model.Profile;
import org.jspecify.annotations.Nullable;

/**
 * What the person's settings say, each value with its line; no password is held.
 *
 * @param localRepository the local repository as written, null when not set
 * @param mirrors the mirrors, in their order
 * @param servers each server id with its line
 * @param activeProfiles the profiles Maven would apply without asking, as Maven's own type
 * @param activeProfileIds the ids the settings name to activate a profile, there or in a pom
 * @param unknowns what depends on where the build runs
 * @param unread what Maven would fill in or refuse, and nothing here does
 */
record UserSettings(
        @Nullable String localRepository,
        List<Mirror> mirrors,
        Map<String, Integer> servers,
        List<Profile> activeProfiles,
        List<String> activeProfileIds,
        List<SettingsCondition> unknowns,
        List<Unread> unread) {

    static final UserSettings NONE =
            new UserSettings(null, List.of(), Map.of(), List.of(), List.of(), List.of(), List.of());

    /** A mirror as the settings write it. */
    record Mirror(
            String id, String url, String mirrorOf, String layouts, boolean blocked, int line) {}

    /** A value of the settings that cannot be taken as it is written. */
    record Unread(String what, String why, int line) {}

    /** A settings profile activated by something other than the settings themselves. */
    record SettingsCondition(String profile, String condition, int line) {}
}
