package org.fuin.porelo.ecosystem.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Extension;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.interpolation.ModelInterpolator;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.apache.maven.model.profile.activation.FileProfileActivator;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.apache.maven.model.profile.activation.PropertyProfileActivator;
import org.apache.maven.model.root.RootLocator;
import org.jspecify.annotations.Nullable;

/**
 * Answers which repository hosts a checkout's build must reach, reading files and running nothing.
 */
public final class RepositoryHosts {
    private static final String ENV_PREFIX = "env.";
    private static final Pattern WRAPPER_DISTRIBUTION =
            Pattern.compile(".*/(apache-maven-[^/\\s]+?)\\.(?:zip|tar\\.gz)");
    private static final String EXTENSIONS = ".mvn/extensions.xml";
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
    private static final String EXTENSION_LOADED =
            "this loads a Maven extension, whose code can change the model; it is not loaded, so"
                    + " what it changes is not in this answer"
                    + OwnFile.HOST_OR_TOOL;

    private final Places places;
    private final UserSettings settings;
    private final BiConsumer<Model, Places> reader;
    private final Path settingsFile;
    private final Mirrors mirrors;
    private final List<Path> localRoots;
    private final Set<RepositoryHost> hosts = new LinkedHashSet<>();
    private final Set<UnknownValue> unknowns = new LinkedHashSet<>();
    // The first pom or parent that went unread: what it would say is not known.
    private @Nullable UnknownValue firstUnread;
    private final Set<Unplaced> unplaced = new LinkedHashSet<>();
    private final PomGuard guard;
    private final DefaultModelBuilder builder;
    private final MavenOptions options;
    private final MachineCondition machineCondition;

    private RepositoryHosts(
            Places places,
            Path settingsFile,
            UserSettings settings,
            List<Path> roots,
            MavenOptions options,
            BiConsumer<Model, Places> reader) {
        this.places = places;
        this.reader = reader;
        this.settings = settings;
        this.settingsFile = settingsFile;
        this.mirrors = new Mirrors(settings.mirrors());
        this.localRoots = roots;
        this.options = options;
        this.machineCondition = new MachineCondition(places.checkout());
        var factory = new GuardedFactory(places, machineCondition);
        this.builder = factory.newInstance();
        this.guard = factory.guard();
    }

    /**
     * Reads a checkout with the settings in the person's home.
     *
     * @param checkout the directory holding the root {@code pom.xml}
     * @param home the home whose {@code .m2} holds the person's settings and local repository
     * @return the answer; what cannot be answered is named in it
     * @throws IOException when the person's settings cannot be read
     * @throws NotAMavenCheckoutException when the checkout holds no {@code pom.xml}
     */
    public static RepositoryHostsAnswer read(Path checkout, Path home) throws IOException {
        return read(checkout, home, (model, places) -> {});
    }

    /** Reads a checkout, handing each pom's effective model to what else needs it. */
    static RepositoryHostsAnswer read(Path checkout, Path home, BiConsumer<Model, Places> reader)
            throws IOException {
        Path absoluteHome = home.toAbsolutePath();
        Path absoluteCheckout = checkout.toAbsolutePath();
        MavenOptions options = MavenOptions.read(absoluteCheckout);
        String named = options.settings();
        Path settingsFile =
                named == null
                        ? absoluteHome.resolve(".m2").resolve("settings.xml")
                        : absoluteCheckout.resolve(named);
        String namedGlobal = options.globalSettings();
        Places places =
                namedGlobal == null
                        ? new Places(absoluteCheckout, absoluteHome, settingsFile)
                        : new Places(
                                absoluteCheckout,
                                absoluteHome,
                                settingsFile,
                                absoluteCheckout.resolve(namedGlobal));
        Path globalFile =
                namedGlobal != null
                        ? absoluteCheckout.resolve(namedGlobal)
                        : wrapperSettings(absoluteCheckout, absoluteHome);
        UserSettings global =
                globalFile == null
                        ? UserSettings.NONE
                        : SettingsReader.read(globalFile, options, places);
        UserSettings settings = SettingsReader.read(settingsFile, options, places).over(global);
        String localRepository = options.property("maven.repo.local");
        List<Path> roots =
                localRoots(
                        localRepository != null ? localRepository : settings.localRepository(),
                        absoluteHome);
        return new RepositoryHosts(places, settingsFile, settings, roots, options, reader)
                .answer(globalFile);
    }

    // The installation the wrapper unpacked, as the only one a home holds for a build to use.
    private static @Nullable Path wrapperSettings(Path checkout, Path home) throws IOException {
        Optional<Matcher> named =
                WrapperProperties.read(checkout)
                        .flatMap(WrapperProperties::url)
                        .map(WRAPPER_DISTRIBUTION::matcher)
                        .filter(Matcher::matches);
        if (named.isEmpty()) {
            return null;
        }
        Matcher name = named.get();
        Path unpacked = home.resolve(".m2/wrapper/dists").resolve(name.group(1));
        if (!Files.isDirectory(unpacked)) {
            return null;
        }
        try (Stream<Path> files = Files.find(unpacked, 4, RepositoryHosts::isConfSettings)) {
            return files.sorted().findFirst().orElse(null);
        }
    }

    private static boolean isConfSettings(Path file, BasicFileAttributes attributes) {
        return attributes.isRegularFile() && file.endsWith("conf/settings.xml");
    }

    private RepositoryHostsAnswer answer(@Nullable Path globalFile) {
        Path checkout = places.checkout();
        Path rootPom = checkout.resolve("pom.xml");
        if (!Files.isRegularFile(rootPom)) {
            throw new NotAMavenCheckoutException(checkout.toString());
        }
        unplaced.addAll(options.notes());
        if (options.settings() != null && !Files.isRegularFile(settingsFile)) {
            unknowns.add(
                    new UnknownValue(
                            options.settings(),
                            "these settings are named for the build and are not there, and Maven"
                                    + " stops on that",
                            options.settingsPlace()));
        }
        if (options.globalSettings() != null
                && (globalFile == null || !Files.isRegularFile(globalFile))) {
            unknowns.add(
                    new UnknownValue(
                            options.globalSettings(),
                            "these global settings are named for the build and are not there, and"
                                    + " Maven stops on that",
                            options.globalSettingsPlace()));
        }
        settings.proxies().forEach(this::answerProxy);
        if (WrapperProperties.unreadable(checkout)) {
            unplaced.add(
                    new Unplaced(
                            "this file is too large or cannot be read, so the Maven the wrapper"
                                    + " fetches and the host it fetches it from are not in this"
                                    + " answer"
                                    + OwnFile.HOST_OR_TOOL,
                            new Place(WrapperProperties.FILE, 0, Origin.CHECKOUT)));
        }
        if (Files.exists(checkout.resolve(EXTENSIONS))) {
            unplaced.add(
                    new Unplaced(
                            "this file loads Maven extensions, which can change the model; they are"
                                    + " not loaded, so what they change is not in this answer"
                                    + OwnFile.HOST_OR_TOOL,
                            new Place(EXTENSIONS, 0, Origin.CHECKOUT)));
        }
        settings.unread()
                .forEach(
                        unreadValue ->
                                unknowns.add(
                                        new UnknownValue(
                                                unreadValue.what(),
                                                unreadValue.why(),
                                                unreadValue.place())));
        settings.unknowns()
                .forEach(
                        condition ->
                                unknowns.add(
                                        new UnknownValue(
                                                "profile " + condition.profile(),
                                                "active only under a condition on "
                                                        + condition.condition()
                                                        + ", which depends on where the build runs",
                                                condition.place())));
        Deque<Path> poms = new ArrayDeque<>(List.of(rootPom));
        Set<Path> seen = new HashSet<>();
        while (!poms.isEmpty()) {
            Path pom = poms.pop();
            // By the real path: links in a checkout can name one pom by endless paths.
            if (seen.add(Places.realOrNormal(pom))) {
                poms.addAll(readPom(pom));
            }
        }
        return new RepositoryHostsAnswer(
                List.copyOf(hosts), List.copyOf(unknowns), List.copyOf(unplaced), firstUnread);
    }

    // In the effective model, so a plugin managed elsewhere counts only once a build uses it.
    private void answerExtensions(Model model, ModelBuildingResult result) {
        if (model.getBuild() != null) {
            model.getBuild().getExtensions().forEach(this::nameExtension);
            PluginManagement managed = model.getBuild().getPluginManagement();
            model.getBuild()
                    .getPlugins()
                    .forEach(
                            plugin -> {
                                answerPlugin(plugin, managed);
                                answerWhatItReaches(plugin, managed);
                            });
        }
        result.getModelIds().stream()
                .map(result::getRawModel)
                .filter(Objects::nonNull)
                .flatMap(raw -> raw.getProfiles().stream())
                .filter(profile -> machineCondition.of(profile.getActivation()) != null)
                .map(Profile::getBuild)
                .filter(Objects::nonNull)
                .map(BuildBase::getPlugins)
                .forEach(plugins -> plugins.forEach(plugin -> answerPlugin(plugin, null)));
    }

    private void answerWhatItReaches(Plugin plugin, @Nullable PluginManagement managed) {
        for (PluginHosts.Reached reached : PluginHosts.of(plugin, managed)) {
            Place place = places.of(reached.location());
            String id = plugin.getArtifactId();
            if (!holdsExpression(reached.url(), place, id)) {
                hostUrl(reached.url(), id, place)
                        .ifPresent(
                                host ->
                                        hosts.add(
                                                new RepositoryHost(
                                                        id,
                                                        host.host(),
                                                        host.url(),
                                                        RepositoryHost.Kind.PLUGIN_REACHES,
                                                        place,
                                                        new ServerReference(id, false, place))));
            }
        }
    }

    private void nameExtension(Extension extension) {
        unplaced.add(new Unplaced(EXTENSION_LOADED, places.of(extension.getLocation(""))));
    }

    private void answerPlugin(Plugin plugin, @Nullable PluginManagement managed) {
        Plugin stated = plugin;
        if (plugin.getExtensions() == null && managed != null) {
            stated =
                    managed.getPlugins().stream()
                            .filter(other -> other.getKey().equals(plugin.getKey()))
                            .findFirst()
                            .orElse(plugin);
        }
        String extensions = stated.getExtensions();
        if (extensions != null && !extensions.equals("false")) {
            InputLocation at = stated.getLocation("extensions");
            unplaced.add(
                    new Unplaced(
                            EXTENSION_LOADED, places.of(at != null ? at : stated.getLocation(""))));
        }
    }

    private List<Path> readPom(Path pom) {
        if (!Files.isRegularFile(pom)) {
            unread(
                    new UnknownValue(
                            pom.getFileName().toString(),
                            "this is no regular file, and is not read",
                            places.of(pom, 0)));
            return List.of();
        }
        try {
            ModelBuildingResult result = builder.build(request(pom));
            takeRefusals();
            answerModel(result, pom);
            reader.accept(result.getEffectiveModel(), places);
            return modulesOf(result.getEffectiveModel(), pom);
        } catch (ModelBuildingException e) {
            answerFailure(e, pom);
            return List.of();
        } catch (OutOfMemoryError | StackOverflowError e) {
            return refused(pom, "filling in its properties grew beyond what can be held");
        } catch (Error e) {
            // A native image holds no part of the model it was not told of, and says so this way.
            return refused(pom, "it reaches a part of Maven's model this executable does not hold");
        } catch (RuntimeException e) {
            // The builder gives up on a broken pom in ways of its own, and one pom must not end
            // the answer for the rest. Its message is not repeated: it can name anything.
            return refused(pom, "Maven's model builder stops on it");
        }
    }

    private List<Path> refused(Path pom, String why) {
        Place place = places.of(pom, 0);
        unread(new UnknownValue(place.file(), "refused: " + why, place));
        return List.of();
    }

    private ModelBuildingRequest request(Path pom) {
        var request = new DefaultModelBuildingRequest();
        // Below this level the builder takes no property given to Maven.
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0);
        request.setProcessPlugins(false);
        // The first phase ends before imports, which bring no repository and no property.
        request.setTwoPhaseBuilding(true);
        request.setLocationTracking(true);
        // Only what the checkout gives the build: the rest of the machine is for where it runs.
        request.setSystemProperties(options.system());
        request.setUserProperties(options.user());
        request.setPomFile(pom.toFile());
        request.setModelSource(new FileModelSource(pom.toFile()));
        // The builder fills these in where they stand, and the next pom must see them as written.
        request.setProfiles(settings.activeProfiles().stream().map(Profile::clone).toList());
        List<String> active = new ArrayList<>(settings.activeProfileIds());
        active.addAll(options.activeProfiles());
        request.setActiveProfileIds(active);
        request.setInactiveProfileIds(options.inactiveProfiles());
        request.setModelResolver(
                new LocalRepositoryResolver(localRoots, places, this::parentUnread));
        return request;
    }

    private void unread(UnknownValue what) {
        if (firstUnread == null) {
            firstUnread = what;
        }
        unknowns.add(what);
    }

    private void parentUnread(UnknownValue parent) {
        unread(parent);
    }

    private void answerFailure(ModelBuildingException failure, Path pom) {
        Place place = places.of(pom, 0);
        if (firstUnread == null) {
            firstUnread = new UnknownValue(place.file(), "Maven's model builder refuses it", place);
        }
        List<UnknownValue> refusals = guard.takeRefusals();
        if (!refusals.isEmpty()) {
            unknowns.addAll(refusals);
            return;
        }
        for (ModelProblem problem : failure.getProblems()) {
            if (problem.getSeverity() != ModelProblem.Severity.WARNING) {
                unknowns.add(
                        new UnknownValue(
                                places.of(pom, 0).file(),
                                "Maven's model builder refuses this pom: "
                                        + places.withoutThePaths(problem.getMessage()),
                                places.of(
                                        problem.getSource(),
                                        Math.max(problem.getLineNumber(), 0))));
            }
        }
    }

    private void takeRefusals() {
        unknowns.addAll(guard.takeRefusals());
    }

    private void answerModel(ModelBuildingResult result, Path pom) {
        Model model = result.getEffectiveModel();
        answerProblems(result, pom);
        answerExtensions(model, result);
        model.getRepositories().forEach(r -> answerRepository(r, RepositoryHost.Kind.REPOSITORY));
        model.getPluginRepositories()
                .forEach(r -> answerRepository(r, RepositoryHost.Kind.PLUGIN_REPOSITORY));
        result.getModelIds().forEach(id -> answerConditionalProfiles(result, id));
    }

    // The first phase goes on after an error, and leaves the value it could not fill in empty.
    private void answerProblems(ModelBuildingResult result, Path pom) {
        for (ModelProblem problem : result.getProblems()) {
            if (problem.getSeverity() != ModelProblem.Severity.WARNING) {
                unknowns.add(
                        new UnknownValue(
                                places.of(pom, 0).file(),
                                "Maven's model builder found a problem in this pom: "
                                        + places.withoutThePaths(problem.getMessage()),
                                places.of(
                                        problem.getSource(),
                                        Math.max(problem.getLineNumber(), 0))));
            }
        }
    }

    private void answerRepository(Repository repository, RepositoryHost.Kind kind) {
        // Maven asks such a repository for nothing.
        if (isOff(repository.getReleases()) && isOff(repository.getSnapshots())) {
            return;
        }
        InputLocation at = repository.getLocation("url");
        Place place = places.of(at != null ? at : repository.getLocation(""));
        String url = repository.getUrl() == null ? "" : repository.getUrl();
        String id = repository.getId();
        if (id == null) {
            unknowns.add(
                    new UnknownValue(
                            "a repository",
                            "its id could not be filled in, so no mirror can be selected for it",
                            place));
            return;
        }
        // Maven selects a mirror before it looks at the URL it replaces.
        Optional<UserSettings.Mirror> mirror = mirrors.of(id, url, repository.getLayout());
        if (mirror.isPresent()) {
            answerMirror(mirror.get());
            answerWhatTheMirrorAsks(mirror.get(), id, url, place);
            return;
        }
        if (holdsExpression(url, place, id)) {
            return;
        }
        hostUrl(url, id, place)
                .ifPresent(
                        host ->
                                hosts.add(
                                        new RepositoryHost(
                                                id,
                                                host.host(),
                                                host.url(),
                                                kind,
                                                place,
                                                server(id, place))));
    }

    private static boolean isOff(@Nullable RepositoryPolicy policy) {
        return policy != null && "false".equalsIgnoreCase(policy.getEnabled());
    }

    // A pattern other than everything can turn the build away, by what could not be filled in.
    private void answerWhatTheMirrorAsks(
            UserSettings.Mirror mirror, String id, String url, Place place) {
        if (mirror.mirrorOf().equals("*")) {
            return;
        }
        String asked = id + (mirror.mirrorOf().contains("external:") ? url : "");
        Matcher matcher = EXPRESSION.matcher(asked);
        while (matcher.find()) {
            unknowns.add(
                    new UnknownValue(
                            "${" + matcher.group(1) + "}",
                            "whether a repository goes to mirror "
                                    + mirror.id()
                                    + " depends on this, which nothing read here fills in",
                            place));
        }
    }

    private void answerMirror(UserSettings.Mirror mirror) {
        Place place = mirror.place();
        String id = mirror.id();
        if (mirror.blocked()) {
            unknowns.add(
                    new UnknownValue(
                            "mirror " + id,
                            "the settings mark it as blocked, so Maven blocks a build that would"
                                    + " go through it, and no host is reached",
                            place));
            return;
        }
        if (holdsExpression(mirror.url(), place, id)) {
            return;
        }
        hostUrl(mirror.url(), id, place)
                .ifPresent(
                        host ->
                                hosts.add(
                                        new RepositoryHost(
                                                id,
                                                host.host(),
                                                host.url(),
                                                RepositoryHost.Kind.MIRROR,
                                                place,
                                                server(id, place),
                                                mirror.mirrorOf())));
    }

    private void answerProxy(UserSettings.Proxy proxy) {
        String url = proxy.protocol() + "://" + proxy.host() + ":" + proxy.port();
        if (holdsExpression(url, proxy.place(), proxy.id())) {
            return;
        }
        hostUrl(url, proxy.id(), proxy.place())
                .ifPresent(
                        host ->
                                hosts.add(
                                        new RepositoryHost(
                                                proxy.id(),
                                                host.host(),
                                                host.url(),
                                                RepositoryHost.Kind.PROXY,
                                                proxy.place(),
                                                new ServerReference(
                                                        proxy.id(),
                                                        proxy.authenticated(),
                                                        proxy.place()))));
    }

    private boolean holdsExpression(String url, Place place, String id) {
        Matcher matcher = EXPRESSION.matcher(url);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String name = matcher.group(1);
            unknowns.add(
                    new UnknownValue(
                            "${" + name + "}",
                            name.startsWith(ENV_PREFIX)
                                    ? "the URL of repository "
                                            + id
                                            + " needs an environment variable, and none is read"
                                    : "the URL of repository "
                                            + id
                                            + " needs this property, and nothing read defines it",
                            place));
        }
        return found;
    }

    private Optional<HostUrl> hostUrl(String url, String id, Place place) {
        Optional<HostUrl> parsed = HostUrl.parse(url);
        if (parsed.isPresent()) {
            return parsed;
        }
        if (HostUrl.isLocalFile(url)) {
            unplaced.add(
                    new Unplaced(
                            "repository "
                                    + id
                                    + " is a local file URL and needs no host; its path must exist in the task",
                            place));
        } else {
            unknowns.add(
                    new UnknownValue(
                            "repository " + id, "its URL names no host that can be read", place));
        }
        return Optional.empty();
    }

    private ServerReference server(String id, Place repositoryPlace) {
        UserSettings.Server server = settings.servers().get(id);
        return server == null
                ? new ServerReference(id, false, repositoryPlace)
                : new ServerReference(id, true, server.place(), server.credential());
    }

    private void answerConditionalProfiles(ModelBuildingResult result, String modelId) {
        Model raw = result.getRawModel(modelId);
        if (raw == null) {
            return;
        }
        for (Profile profile : raw.getProfiles()) {
            String condition = machineCondition.of(profile.getActivation());
            if (condition != null && holdsSomething(profile)) {
                InputLocation id = profile.getLocation("id");
                Place place = places.of(id != null ? id : profile.getLocation(""));
                unknowns.add(
                        new UnknownValue(
                                "profile " + profile.getId(),
                                "active only under a condition on "
                                        + condition
                                        + ", which depends on where the build runs; what it holds"
                                        + " is not answered",
                                place));
            }
        }
    }

    private static boolean holdsSomething(Profile profile) {
        return !profile.getRepositories().isEmpty()
                || !profile.getPluginRepositories().isEmpty()
                || !profile.getProperties().isEmpty()
                || !profile.getModules().isEmpty();
    }

    private List<Path> modulesOf(Model model, Path pom) {
        List<Path> modules = new ArrayList<>();
        Path directory = pom.toAbsolutePath().normalize().resolveSibling("");
        InputLocation lines = model.getLocation("modules");
        for (int i = 0; i < model.getModules().size(); i++) {
            String module = model.getModules().get(i);
            Path target = directory.resolve(module.replace('\\', File.separatorChar)).normalize();
            Path modulePom = Files.isDirectory(target) ? target.resolve("pom.xml") : target;
            if (Files.isRegularFile(modulePom)) {
                modules.add(modulePom);
            } else {
                unknowns.add(
                        new UnknownValue(
                                "module " + places.withoutThePaths(module),
                                "it is missing, and is not read",
                                placeOfModule(lines, i, pom)));
            }
        }
        return modules;
    }

    private Place placeOfModule(@Nullable InputLocation lines, int index, Path pom) {
        InputLocation line = lines == null ? null : lines.getLocation(index);
        return line == null ? places.of(pom, 0) : places.of(line);
    }

    // Maven's own default, and the parts its split local repository uses.
    private static List<Path> localRoots(@Nullable String written, Path home) throws IOException {
        Path chosen =
                written == null ? null : Path.of(written.replace("${user.home}", home.toString()));
        // Empty is Maven's default too; a relative path would widen what is read to wherever this
        // process happens to run.
        Path base =
                chosen == null || !chosen.isAbsolute()
                        ? home.resolve(".m2").resolve("repository")
                        : chosen;
        List<Path> roots =
                new ArrayList<>(List.of(base, base.resolve("cached"), base.resolve("installed")));
        Path installed = base.resolve("installed");
        if (Files.isDirectory(installed)) {
            try (Stream<Path> parts = Files.list(installed)) {
                parts.filter(Files::isDirectory).sorted().forEach(roots::add);
            }
        }
        return roots;
    }

    /**
     * The checkout itself: Maven's own looks for a directory above it, and fails on finding none.
     */
    private record CheckoutRoot(Path checkout) implements RootLocator {
        @Override
        public Path findRoot(Path basedir) {
            return checkout;
        }

        @Override
        public Path findMandatoryRoot(Path basedir) {
            return checkout;
        }
    }

    /** The builder with its one pom reader guarded, and asking the machine for no activation. */
    private static final class GuardedFactory extends DefaultModelBuilderFactory {
        private final Places places;
        private final MachineCondition machineCondition;
        private @Nullable PomGuard guard;

        GuardedFactory(Places places, MachineCondition machineCondition) {
            this.places = places;
            this.machineCondition = machineCondition;
        }

        PomGuard guard() {
            return java.util.Objects.requireNonNull(guard);
        }

        @Override
        protected ModelProcessor newModelProcessor() {
            PomGuard created = new PomGuard(super.newModelProcessor(), places);
            // The builder asks first; the second is the super pom's, and reads nothing else.
            if (guard == null) {
                guard = created;
            }
            return created;
        }

        @Override
        protected ModelInterpolator newModelInterpolator() {
            return new StringVisitorModelInterpolator()
                    .setPathTranslator(newPathTranslator())
                    .setUrlNormalizer(newUrlNormalizer())
                    .setVersionPropertiesProcessor(newModelVersionPropertiesProcessor())
                    .setRootLocator(new CheckoutRoot(places.checkout()));
        }

        @Override
        protected ProfileActivator[] newProfileActivators() {
            List<ProfileActivator> activators = new ArrayList<>(List.of(machineCondition));
            for (ProfileActivator activator : super.newProfileActivators()) {
                if (activator instanceof PropertyProfileActivator
                        || activator instanceof FileProfileActivator) {
                    activators.add(activator);
                }
            }
            return activators.toArray(new ProfileActivator[0]);
        }
    }
}
