package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelBuildingResult;
import org.jspecify.annotations.Nullable;

/**
 * Follows what filled a value in to whoever wrote it, in the order Maven lets one win. What it
 * cannot follow it names, and the value is then nobody's: guessing would let a forge pass as the
 * person. A trace counts only if it reproduces the value the builder arrived at, since the
 * builder's ways of filling in are more than can be followed here.
 */
final class PropertyPlaces {
    static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> TRACED_FIELDS =
            Set.of("version", "groupId", "artifactId", "name");
    // The builder's own sources come before the properties, so a property of the name cannot win.
    private static final Set<String> FROM_THE_BUILD =
            Set.of("basedir", "rootDirectory", "build.timestamp", "maven.build.timestamp");

    private static final String BUILT_IN_CENTRAL = "maven.repo.central";
    private static final String BUILT_IN_CENTRAL_URL = "https://repo.maven.apache.org/maven2";
    private static final int MAX_REPRODUCED_CHARS = 1024 * 1024;

    private final ModelBuildingResult result;
    private final Places places;
    private final UserSettings settings;
    private final Place settingsFile;

    PropertyPlaces(
            ModelBuildingResult result, Places places, UserSettings settings, Place settingsFile) {
        this.result = result;
        this.places = places;
        this.settings = settings;
        this.settingsFile = settingsFile;
    }

    /** What a raw value was filled in from, and the expressions that could not be followed. */
    record Trace(List<PropertyUse> uses, List<String> untraced) {}

    Trace of(String rawValue) {
        Trace trace = new Trace(new ArrayList<>(), new ArrayList<>());
        collect(rawValue, new HashSet<>(), trace);
        return trace;
    }

    /** Whether what the raw value is traced to, filled in, gives what the builder arrived at. */
    boolean reproduces(@Nullable String rawValue, @Nullable String effective) {
        if (rawValue == null) {
            return effective == null;
        }
        return Objects.equals(filled(rawValue, new HashMap<>(), new HashSet<>()), effective);
    }

    // Remembers each answer, so a value that doubles costs a step per level.
    private @Nullable String filled(String rawValue, Map<String, String> known, Set<String> open) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = EXPRESSION.matcher(rawValue);
        int end = 0;
        while (matcher.find()) {
            result.append(rawValue, end, matcher.start());
            end = matcher.end();
            String name = nameIn(matcher);
            String value = known.get(name);
            if (value == null) {
                Found found = open.add(name) ? find(name) : null;
                value = found == null ? matcher.group() : filled(found.rawValue(), known, open);
                open.remove(name);
                if (value == null) {
                    return null;
                }
                if (found != null) {
                    known.put(name, value);
                }
            }
            result.append(value);
            if (result.length() > MAX_REPRODUCED_CHARS) {
                return null;
            }
        }
        return result.append(rawValue, end, rawValue.length()).toString();
    }

    // The builder drops one leading dot before it looks a name up.
    private static String nameIn(Matcher matcher) {
        String written = matcher.group(1);
        return written.startsWith(".") ? written.substring(1) : written;
    }

    private void collect(String rawValue, Set<String> seen, Trace trace) {
        Matcher matcher = EXPRESSION.matcher(rawValue);
        while (matcher.find()) {
            String name = nameIn(matcher);
            if (seen.add(name)) {
                Found found = find(name);
                if (found == null) {
                    trace.untraced().add(SafeText.of(name));
                } else {
                    trace.uses().add(new PropertyUse(SafeText.of(name), found.place()));
                    collect(found.rawValue(), seen, trace);
                }
            }
        }
    }

    private @Nullable Found find(String name) {
        if (FROM_THE_BUILD.contains(name)) {
            return null;
        }
        // Under these prefixes the model's fields come before any property.
        if (name.startsWith("project.") || name.startsWith("pom.")) {
            Found field = field(name.substring(name.indexOf('.') + 1));
            return field != null ? field : property(name);
        }
        Found property = property(name);
        if (property != null) {
            return property;
        }
        // Maven's own last source, which only the super pom asks for.
        return name.equals(BUILT_IN_CENTRAL)
                ? new Found(new Place("", 0, Origin.MACHINE), BUILT_IN_CENTRAL_URL)
                : field(name);
    }

    private @Nullable Found property(String name) {
        List<Profile> active = settings.activeProfiles();
        for (int i = active.size() - 1; i >= 0; i--) {
            String value = active.get(i).getProperties().getProperty(name);
            if (value != null) {
                int line = settings.propertyPlaces().getOrDefault(name, 0);
                return new Found(
                        new Place(settingsFile.file(), line, settingsFile.origin()), value);
            }
        }
        for (String id : result.getModelIds()) {
            Model raw = result.getRawModel(id);
            Found found = raw == null ? null : inModel(raw, id, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private @Nullable Found inModel(Model raw, String id, String name) {
        List<Profile> active = result.getActivePomProfiles(id);
        if (active != null) {
            for (int i = active.size() - 1; i >= 0; i--) {
                Profile profile = active.get(i);
                String value = profile.getProperties().getProperty(name);
                if (value != null) {
                    return new Found(placeOf(profile.getLocation("properties"), name), value);
                }
            }
        }
        String value = raw.getProperties().getProperty(name);
        return value == null
                ? null
                : new Found(placeOf(raw.getLocation("properties"), name), value);
    }

    // A field stands where the first pom that sets it set it; a parent element sets no location,
    // so a value taken from there is not followed.
    private @Nullable Found field(String field) {
        if (!TRACED_FIELDS.contains(field)) {
            return null;
        }
        for (String id : result.getModelIds()) {
            Model raw = result.getRawModel(id);
            InputLocation location = raw == null ? null : raw.getLocation(field);
            if (location != null) {
                return new Found(places.of(location), textOf(raw, field));
            }
            // A name is not inherited, so a parent's is no answer for the pom built.
            if (field.equals("name")) {
                return null;
            }
        }
        return null;
    }

    private static String textOf(Model raw, String field) {
        String value =
                switch (field) {
                    case "version" -> raw.getVersion();
                    case "groupId" -> raw.getGroupId();
                    case "artifactId" -> raw.getArtifactId();
                    default -> raw.getName();
                };
        return value == null ? "" : value;
    }

    private Place placeOf(@Nullable InputLocation properties, String name) {
        if (properties == null) {
            return new Place("", 0, Origin.MACHINE);
        }
        InputLocation own = properties.getLocation(name);
        return places.of(own == null ? properties : own);
    }

    private record Found(Place place, String rawValue) {}
}
