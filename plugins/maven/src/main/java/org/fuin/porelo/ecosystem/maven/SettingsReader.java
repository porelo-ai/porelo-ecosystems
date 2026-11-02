package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.maven.model.Activation;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.jspecify.annotations.Nullable;

/**
 * Reads the person's settings with the line of each value, which Maven's settings model has not. It
 * takes only the elements it names, so a password's text is never held.
 */
final class SettingsReader {
    private static final int MAX_SETTINGS_BYTES = 4 * 1024 * 1024;
    private static final Set<String> CONDITIONS = Set.of("jdk", "os", "file", "property");
    private static final String MIRRORS = "settings/mirrors";
    private static final String MIRROR = MIRRORS + "/mirror";
    private static final String SERVER = "settings/servers/server";
    private static final String ACTIVE_PROFILES = "settings/activeProfiles";
    private static final String PROFILES = "settings/profiles";
    private static final String PROFILE = PROFILES + "/profile";
    private static final String REPOSITORIES = PROFILE + "/repositories";
    private static final String PLUGIN_REPOSITORIES = PROFILE + "/pluginRepositories";
    private static final String REPOSITORY = REPOSITORIES + "/repository";
    private static final String PLUGIN_REPOSITORY = PLUGIN_REPOSITORIES + "/pluginRepository";
    private static final String DEFAULT_LAYOUTS = "default,legacy";
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");

    private final Path file;
    private final MavenOptions options;
    private final InputSource source = new InputSource();
    private final Deque<String> path = new ArrayDeque<>();
    private @Nullable String localRepository;
    private final List<UserSettings.Mirror> mirrors = new ArrayList<>();
    private final Map<String, Integer> servers = new LinkedHashMap<>();
    private final List<ProfileDraft> profiles = new ArrayList<>();
    private final Set<String> activeIds = new LinkedHashSet<>();
    private final List<UserSettings.Unread> unread = new ArrayList<>();
    private @Nullable ProfileDraft profile;
    private @Nullable MirrorDraft mirror;
    private @Nullable RepositoryDraft repository;

    private SettingsReader(Path file, MavenOptions options) {
        this.file = file;
        this.options = options;
        source.setLocation(file.toString());
    }

    static UserSettings read(Path file, MavenOptions options) throws IOException {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) {
            bytes = in.readNBytes(MAX_SETTINGS_BYTES + 1);
        } catch (NoSuchFileException e) {
            return UserSettings.NONE;
        }
        if (bytes.length > MAX_SETTINGS_BYTES) {
            throw new IOException("larger than " + MAX_SETTINGS_BYTES + " bytes: " + file);
        }
        return new SettingsReader(file, options).parse(bytes);
    }

    private UserSettings parse(byte[] bytes) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        try (var in = new java.io.ByteArrayInputStream(bytes)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) {
                    throw new IOException("it declares a DTD: " + file);
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    start(reader);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    end(reader.getLocalName());
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(
                    "not readable: " + file + ": line " + e.getLocation().getLineNumber(), e);
        }
        return settings();
    }

    private void start(XMLStreamReader reader) throws XMLStreamException {
        String name = reader.getLocalName();
        String parent = where();
        int line = reader.getLocation().getLineNumber();
        int column = reader.getLocation().getColumnNumber();
        if (parent.equals(MIRRORS) && name.equals("mirror")) {
            mirror = new MirrorDraft();
            mirror.line = line;
        } else if (parent.equals(PROFILES) && name.equals("profile")) {
            profile = new ProfileDraft();
            profiles.add(profile);
            profile.line = line;
        } else if (parent.equals(REPOSITORIES) && name.equals("repository")) {
            repository = new RepositoryDraft();
        } else if (parent.equals(PLUGIN_REPOSITORIES) && name.equals("pluginRepository")) {
            repository = new RepositoryDraft();
            repository.plugin = true;
        }
        if (name.equals("localRepository") && parent.equals("settings")) {
            localRepository = text(reader);
            nameWhatMavenFillsIn(
                    localRepository.replace("${user.home}", ""),
                    localRepository,
                    "the local repository",
                    line);
            return;
        }
        if (name.equals("id") && parent.equals(SERVER)) {
            servers.putIfAbsent(text(reader), line);
            return;
        }
        if (name.equals("activeProfile") && parent.equals(ACTIVE_PROFILES)) {
            String id = text(reader);
            activeIds.add(id);
            nameWhatMavenFillsIn(id, id, "an active profile", line);
            return;
        }
        if (parent.equals(MIRROR) && mirror != null && takeMirror(reader, name, line)) {
            return;
        }
        if (parent.equals(REPOSITORY) || parent.equals(PLUGIN_REPOSITORY)) {
            if (repository != null && takeRepository(reader, name, line, column)) {
                return;
            }
        }
        if (repository != null && name.equals("enabled") && takePolicy(reader, parent)) {
            return;
        }
        if (profile != null && takeProfile(reader, name, parent)) {
            return;
        }
        path.push(name);
    }

    // The full path, since a name alone says nothing: a property may be called profile.
    private String where() {
        StringBuilder joined = new StringBuilder();
        path.descendingIterator()
                .forEachRemaining(name -> joined.append(joined.isEmpty() ? "" : "/").append(name));
        return joined.toString();
    }

    private boolean takeMirror(XMLStreamReader reader, String name, int line)
            throws XMLStreamException {
        MirrorDraft draft = java.util.Objects.requireNonNull(mirror);
        switch (name) {
            case "id" -> draft.id = text(reader);
            case "url" -> {
                draft.url = text(reader);
                draft.line = line;
            }
            case "mirrorOf" -> draft.mirrorOf = text(reader);
            case "mirrorOfLayouts" -> draft.layouts = text(reader);
            case "blocked" -> draft.blocked = text(reader).equals("true");
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean takeRepository(XMLStreamReader reader, String name, int line, int column)
            throws XMLStreamException {
        RepositoryDraft draft = java.util.Objects.requireNonNull(repository);
        switch (name) {
            case "id" -> draft.id = text(reader);
            case "layout" -> draft.layout = text(reader);
            case "url" -> {
                draft.url = text(reader);
                draft.urlLocation = new InputLocation(line, column, source);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean takePolicy(XMLStreamReader reader, String parent) throws XMLStreamException {
        RepositoryDraft draft = java.util.Objects.requireNonNull(repository);
        boolean inRepository =
                parent.startsWith(REPOSITORY) || parent.startsWith(PLUGIN_REPOSITORY);
        if (inRepository && parent.endsWith("/releases")) {
            draft.releases = text(reader);
            return true;
        }
        if (inRepository && parent.endsWith("/snapshots")) {
            draft.snapshots = text(reader);
            return true;
        }
        return false;
    }

    // Maven fills the settings in from where the build runs; nothing here reads that.
    private void nameWhatMavenFillsIn(String checked, String written, String what, int line) {
        if (checked.contains("${")) {
            unread.add(
                    new UserSettings.Unread(
                            written,
                            what + " is written with this, which depends on where the build runs",
                            line));
        }
    }

    private boolean takeProfile(XMLStreamReader reader, String name, String parent)
            throws XMLStreamException {
        ProfileDraft draft = java.util.Objects.requireNonNull(profile);
        if (parent.equals(PROFILE) && name.equals("id")) {
            draft.id = text(reader);
            return true;
        }
        if (parent.equals(PROFILE + "/properties")) {
            draft.properties.put(name, text(reader));
            return true;
        }
        String activation = PROFILE + "/activation";
        if (parent.equals(activation) && name.equals("activeByDefault")) {
            draft.activeByDefault = text(reader).equals("true");
            return true;
        }
        if (parent.equals(activation) && CONDITIONS.contains(name)) {
            draft.conditions.add(name);
        }
        return false;
    }

    private void end(String name) {
        if (!path.isEmpty()) {
            path.pop();
        }
        String parent = where();
        if (parent.equals(MIRRORS) && name.equals("mirror")) {
            finishMirror(java.util.Objects.requireNonNull(mirror));
            mirror = null;
        } else if (parent.equals(REPOSITORIES) && name.equals("repository")
                || parent.equals(PLUGIN_REPOSITORIES) && name.equals("pluginRepository")) {
            if (profile != null && repository != null) {
                profile.repositories.add(repository);
            }
            repository = null;
        } else if (parent.equals(PROFILES) && name.equals("profile")) {
            profile = null;
        }
    }

    private void finishMirror(MirrorDraft draft) {
        String id = draft.id == null ? "default" : draft.id;
        if (draft.url == null || draft.mirrorOf == null) {
            unread.add(
                    new UserSettings.Unread(
                            "mirror " + id,
                            "it lacks a URL or what it mirrors, and Maven refuses such settings",
                            draft.line));
            return;
        }
        nameWhatMavenFillsIn(draft.mirrorOf, draft.mirrorOf, "what a mirror covers", draft.line);
        mirrors.add(
                new UserSettings.Mirror(
                        id, draft.url, draft.mirrorOf, draft.layouts, draft.blocked, draft.line));
    }

    private UserSettings settings() {
        List<Profile> active = new ArrayList<>();
        List<UserSettings.SettingsCondition> conditional = new ArrayList<>();
        for (ProfileDraft draft : profiles) {
            if (activeIds.contains(draft.id) || draft.activeByDefault) {
                active.add(draft.toProfile());
            } else if (!draft.conditions.isEmpty()) {
                conditional.add(
                        new UserSettings.SettingsCondition(
                                draft.id, String.join(", ", draft.conditions), draft.line));
            }
        }
        return new UserSettings(
                localRepository,
                mirrors,
                servers,
                active,
                List.copyOf(activeIds),
                conditional,
                unread);
    }

    // Maven fills in the settings when it reads them, before any pom's property counts.
    private String text(XMLStreamReader reader) throws XMLStreamException {
        String written = reader.getElementText().strip();
        return EXPRESSION
                .matcher(written)
                .replaceAll(
                        match -> {
                            String value = options.property(match.group(1));
                            return Matcher.quoteReplacement(value != null ? value : match.group());
                        });
    }

    private static final class MirrorDraft {
        @Nullable String id;
        @Nullable String url;
        @Nullable String mirrorOf;
        String layouts = DEFAULT_LAYOUTS;
        boolean blocked;
        int line;
    }

    private static final class RepositoryDraft {
        @Nullable String id;
        @Nullable String url;
        @Nullable InputLocation urlLocation;
        @Nullable String layout;
        @Nullable String releases;
        @Nullable String snapshots;
        boolean plugin;
    }

    private static final class ProfileDraft {
        String id = "default";
        int line;
        boolean activeByDefault;
        final List<String> conditions = new ArrayList<>();
        final Map<String, String> properties = new LinkedHashMap<>();
        final List<RepositoryDraft> repositories = new ArrayList<>();

        Profile toProfile() {
            Profile result = new Profile();
            result.setId(id);
            // Its id named nowhere: a pom's profile of the same id stays as it is.
            result.setSource(Profile.SOURCE_SETTINGS);
            if (activeByDefault) {
                Activation activation = new Activation();
                activation.setActiveByDefault(true);
                result.setActivation(activation);
            }
            properties.forEach(result::addProperty);
            for (RepositoryDraft draft : repositories) {
                if (draft.url == null) {
                    continue;
                }
                Repository repository = new Repository();
                repository.setId(draft.id == null ? "default" : draft.id);
                repository.setUrl(draft.url);
                repository.setLocation("url", draft.urlLocation);
                repository.setReleases(policy(draft.releases));
                repository.setSnapshots(policy(draft.snapshots));
                if (draft.layout != null) {
                    repository.setLayout(draft.layout);
                }
                if (draft.plugin) {
                    result.addPluginRepository(repository);
                } else {
                    result.addRepository(repository);
                }
            }
            return result;
        }

        private static @Nullable RepositoryPolicy policy(@Nullable String enabled) {
            if (enabled == null) {
                return null;
            }
            RepositoryPolicy policy = new RepositoryPolicy();
            policy.setEnabled(enabled);
            return policy;
        }
    }
}
