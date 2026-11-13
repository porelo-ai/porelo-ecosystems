package org.fuin.porelo.ecosystem.maven;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.fuin.porelo.ecosystem.api.EcosystemRefused;
import org.fuin.porelo.ecosystem.api.Extension;
import org.fuin.porelo.ecosystem.api.Repository;

/**
 * The project's {@code extensions.maven} block, which Porelo passes unread: what each repository's
 * mirror takes, and the settings file the checkout holds for its build. A block that does not fit
 * is refused naming its key, never a value.
 */
final class MavenExtension {
    private static final String KEY = "extensions.maven";
    private static final String MIRROR_OF = "mirrorOf";
    private static final String OWN_SETTINGS = "ownSettings";
    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_.:*!,-]+");

    private MavenExtension() {}

    /**
     * What each repository's mirror takes, by the repository's name.
     *
     * @param extension the block as the project holds it
     * @param repositories the repositories the task reaches
     * @return each named repository's {@code <mirrorOf>}; a repository not named takes itself
     * @throws EcosystemRefused when the block holds what this adapter does not read
     */
    static Map<String, String> mirrorOf(Extension extension, List<Repository> repositories) {
        Map<String, Object> block = extension.values();
        for (String key : block.keySet()) {
            if (!key.equals(MIRROR_OF) && !key.equals(OWN_SETTINGS)) {
                throw refused(KEY + "." + key, "is not a key this adapter reads");
            }
        }
        Object written = block.get(MIRROR_OF);
        if (written == null) {
            return Map.of();
        }
        if (!(written instanceof Map<?, ?> byName)) {
            throw refused(KEY + "." + MIRROR_OF, "is not a mapping of repository names");
        }
        Set<String> names = repositories.stream().map(Repository::name).collect(Collectors.toSet());
        Map<String, String> taken = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : byName.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String key = KEY + "." + MIRROR_OF + "." + name;
            if (!names.contains(name)) {
                throw refused(key, "names no repository of the project");
            }
            if (!(entry.getValue() instanceof String pattern)
                    || !PATTERN.matcher(pattern).matches()) {
                throw refused(key, "is not a pattern of Maven's ids");
            }
            taken.put(name, pattern);
        }
        return taken;
    }

    /**
     * Whether the checkout holds the settings file its build uses, so the task gets none generated.
     *
     * @param extension the block as the project holds it
     * @return true when the block names such a file
     * @throws EcosystemRefused when the path is not one inside the checkout
     */
    static boolean ownSettings(Extension extension) {
        Object written = extension.values().get(OWN_SETTINGS);
        if (written == null) {
            return false;
        }
        if (!(written instanceof String path) || !OwnSettings.staysInside(path)) {
            throw refused(KEY + "." + OWN_SETTINGS, "is not a path inside the checkout");
        }
        return true;
    }

    private static EcosystemRefused refused(String key, String why) {
        return new EcosystemRefused(EcosystemRefused.Reason.UNREADABLE, key + " " + why);
    }
}
