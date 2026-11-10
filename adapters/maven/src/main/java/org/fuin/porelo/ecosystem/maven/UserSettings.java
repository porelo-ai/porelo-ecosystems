package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.model.Profile;
import org.jspecify.annotations.Nullable;

/**
 * What the person's settings say, each value with its line; no password is held.
 *
 * @param localRepository the local repository as written, null when not set
 * @param mirrors the mirrors, in their order
 * @param servers each server by its id, with its line and what it sends
 * @param activeProfiles the profiles Maven would apply without asking, as Maven's own type
 * @param activeProfileIds the ids the settings name to activate a profile, there or in a pom
 * @param unknowns what depends on where the build runs
 * @param unread what Maven would fill in or refuse, and nothing here does
 * @param idleProfiles the profiles nothing in their own file activates, which another file can
 * @param proxies the active proxies
 */
record UserSettings(
        @Nullable String localRepository,
        List<Mirror> mirrors,
        Map<String, Server> servers,
        List<Profile> activeProfiles,
        List<String> activeProfileIds,
        List<SettingsCondition> unknowns,
        List<Unread> unread,
        List<Profile> idleProfiles,
        List<Proxy> proxies) {

    static final UserSettings NONE =
            new UserSettings(
                    null, List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of());

    /** A mirror as the settings write it. */
    record Mirror(
            String id, String url, String mirrorOf, String layouts, boolean blocked, Place place) {}

    /** A value of the settings that cannot be taken as it is written. */
    record Unread(String what, String why, Place place) {}

    /** A server, with what it sends; none of its secrets. */
    record Server(Place place, @Nullable Credential credential) {}

    /** A settings profile activated by something other than the settings themselves. */
    record SettingsCondition(String profile, String condition, Place place) {}

    /** A proxy the build goes through; of its credential only whether it has one is held. */
    record Proxy(
            String id,
            String protocol,
            String host,
            String port,
            String nonProxyHosts,
            boolean authenticated,
            Place place) {}

    /** These settings over a global file's: by id theirs give way, as Maven merges the two. */
    UserSettings over(UserSettings global) {
        Set<String> ids = new LinkedHashSet<>(activeProfileIds);
        ids.addAll(global.activeProfileIds);
        Map<String, Mirror> allMirrors = new LinkedHashMap<>();
        mirrors.forEach(m -> allMirrors.put(m.id(), m));
        global.mirrors.forEach(m -> allMirrors.putIfAbsent(m.id(), m));
        Map<String, Server> allServers = new LinkedHashMap<>(servers);
        global.servers.forEach(allServers::putIfAbsent);
        Map<String, Proxy> allProxies = new LinkedHashMap<>();
        proxies.forEach(p -> allProxies.put(p.id(), p));
        global.proxies.forEach(p -> allProxies.putIfAbsent(p.id(), p));
        Map<String, Profile> allActive = new LinkedHashMap<>();
        Map<String, Profile> allIdle = new LinkedHashMap<>();
        Set<String> mine = new LinkedHashSet<>();
        for (Profile profile : activeProfiles) {
            allActive.put(profile.getId(), profile);
            mine.add(profile.getId());
        }
        idleProfiles.forEach(p -> mine.add(p.getId()));
        idleProfiles.forEach(p -> allIdle.put(p.getId(), p));
        for (Profile profile : global.activeProfiles) {
            if (!mine.contains(profile.getId())) {
                allActive.put(profile.getId(), profile);
            }
        }
        for (Profile profile : global.idleProfiles) {
            if (!mine.contains(profile.getId())) {
                allIdle.put(profile.getId(), profile);
            }
        }
        // A file names an id for the other file's profile too, on any condition of that profile.
        ids.stream()
                .map(allIdle::remove)
                .filter(Objects::nonNull)
                .forEach(p -> allActive.put(p.getId(), p));
        List<SettingsCondition> conditions = new ArrayList<>(unknowns);
        global.unknowns.stream().filter(c -> !mine.contains(c.profile())).forEach(conditions::add);
        conditions.removeIf(c -> allActive.containsKey(c.profile()));
        List<Unread> allUnread = new ArrayList<>(unread);
        allUnread.addAll(global.unread);
        return new UserSettings(
                localRepository != null ? localRepository : global.localRepository,
                List.copyOf(allMirrors.values()),
                allServers,
                List.copyOf(allActive.values()),
                List.copyOf(ids),
                conditions,
                allUnread,
                List.copyOf(allIdle.values()),
                List.copyOf(allProxies.values()));
    }
}
