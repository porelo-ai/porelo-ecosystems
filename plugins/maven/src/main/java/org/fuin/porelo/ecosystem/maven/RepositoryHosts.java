package org.fuin.porelo.ecosystem.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.maven.model.Activation;
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
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.apache.maven.model.root.RootLocator;
import org.jspecify.annotations.Nullable;

/**
 * Answers which repository hosts a checkout's build must reach, reading files and running nothing.
 */
public final class RepositoryHosts {
    private static final String ENV_PREFIX = "env.";
    private static final String EXTENSIONS = ".mvn/extensions.xml";
    private static final String MAVEN_CONFIG = ".mvn/maven.config";
    private static final String JVM_CONFIG = ".mvn/jvm.config";
    private static final String EXTENSION_LOADED =
            "this loads a Maven extension, whose code can change the model; it is not loaded, so"
                    + " the model is not trusted";

    private final Places places;
    private final UserSettings settings;
    private final Place settingsPlace;
    private final Path settingsFile;
    private final Mirrors mirrors;
    private final List<Path> localRoots;
    private final Set<RepositoryHost> hosts = new LinkedHashSet<>();
    private final Set<UnknownValue> unknowns = new LinkedHashSet<>();
    private final Set<Unplaced> unplaced = new LinkedHashSet<>();
    private final PomGuard guard;
    private final DefaultModelBuilder builder;
    private boolean trusted = true;

    private RepositoryHosts(
            Path checkout, Path home, Path settingsFile, UserSettings settings, List<Path> roots) {
        this.places = new Places(checkout, home, settingsFile, roots);
        this.settings = settings;
        this.settingsFile = settingsFile;
        this.settingsPlace = places.of(settingsFile, 0);
        this.mirrors = new Mirrors(settings.mirrors());
        this.localRoots = roots;
        var factory = new GuardedFactory(places);
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
        Path absoluteHome = home.toAbsolutePath();
        Path settingsFile = absoluteHome.resolve(".m2").resolve("settings.xml");
        UserSettings settings = SettingsReader.read(settingsFile);
        List<Path> roots = localRoots(settings.localRepository(), absoluteHome);
        return new RepositoryHosts(checkout, absoluteHome, settingsFile, settings, roots).answer();
    }

    private RepositoryHostsAnswer answer() {
        Path checkout = places.checkout();
        Path rootPom = checkout.resolve("pom.xml");
        if (!Files.isRegularFile(rootPom)) {
            throw new NotAMavenCheckoutException(checkout.toString());
        }
        namedFiles(checkout);
        settings.unread()
                .forEach(
                        unreadValue ->
                                unknowns.add(
                                        new UnknownValue(
                                                unreadValue.what(),
                                                unreadValue.why(),
                                                new Place(
                                                        settingsPlace.file(),
                                                        unreadValue.line(),
                                                        settingsPlace.origin()))));
        settings.unknowns()
                .forEach(
                        condition ->
                                unknowns.add(
                                        new UnknownValue(
                                                "profile " + SafeText.of(condition.profile()),
                                                "active only under a condition on "
                                                        + condition.condition()
                                                        + ", which depends on where the build runs",
                                                new Place(
                                                        settingsPlace.file(),
                                                        condition.line(),
                                                        settingsPlace.origin()))));
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
                List.copyOf(hosts), List.copyOf(unknowns), List.copyOf(unplaced), trusted);
    }

    private void namedFiles(Path checkout) {
        optionsFile(checkout, MAVEN_CONFIG, OptionFiles.MAVEN_OPTIONS);
        optionsFile(checkout, JVM_CONFIG, OptionFiles.JVM_OPTIONS);
        if (Files.exists(checkout.resolve(EXTENSIONS))) {
            distrust(
                    "this file loads Maven extensions, which can change the model; they are"
                            + " not loaded, so the model is not trusted",
                    new Place(EXTENSIONS, 0, Origin.CHECKOUT));
        }
    }

    private void optionsFile(Path checkout, String name, Pattern harmless) {
        Path file = checkout.resolve(name);
        if (Files.exists(file) && !OptionFiles.leaveTheModelAlone(file, harmless, places)) {
            distrust(
                    "this file holds options that can change the model or run code; they are not"
                            + " applied, so the model is not trusted",
                    new Place(name, 0, Origin.CHECKOUT));
        }
    }

    private void distrust(String why, Place place) {
        trusted = false;
        unplaced.add(new Unplaced(why, place));
    }

    // In the effective model, so a plugin managed elsewhere counts only once a build uses it.
    private void answerExtensions(Model model, ModelBuildingResult result) {
        if (model.getBuild() != null) {
            model.getBuild().getExtensions().forEach(this::distrustExtension);
            PluginManagement managed = model.getBuild().getPluginManagement();
            model.getBuild().getPlugins().forEach(plugin -> answerPlugin(plugin, managed));
        }
        result.getModelIds().stream()
                .map(result::getRawModel)
                .filter(Objects::nonNull)
                .flatMap(raw -> raw.getProfiles().stream())
                .filter(profile -> conditionOf(profile.getActivation()) != null)
                .map(Profile::getBuild)
                .filter(Objects::nonNull)
                .map(BuildBase::getPlugins)
                .forEach(plugins -> plugins.forEach(plugin -> answerPlugin(plugin, null)));
    }

    private void distrustExtension(Extension extension) {
        distrust(EXTENSION_LOADED, places.of(extension.getLocation("")));
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
            distrust(EXTENSION_LOADED, places.of(at != null ? at : stated.getLocation("")));
        }
    }

    private List<Path> readPom(Path pom) {
        if (!places.isInTheCheckout(pom)) {
            unknowns.add(
                    new UnknownValue(
                            SafeText.of(pom.getFileName().toString()),
                            "this pom lies outside the checkout, or is no regular file, and is not read",
                            places.asNamedInTheCheckout(pom)));
            return List.of();
        }
        try {
            ModelBuildingResult result = builder.build(request(pom));
            unknowns.addAll(guard.takeOutside());
            takeRefusals();
            answerModel(result, pom);
            return modulesOf(result.getEffectiveModel(), pom);
        } catch (ModelBuildingException e) {
            answerFailure(e, pom);
            return List.of();
        } catch (BoundedInterpolator.FillingRefused e) {
            return refused(pom, "its values would grow beyond what is read");
        } catch (OutOfMemoryError | StackOverflowError e) {
            return refused(pom, "filling in its properties grew beyond what can be held");
        } catch (RuntimeException e) {
            // The builder gives up on a hostile pom in ways of its own, and one pom must not end
            // the answer for the rest. Its message is not repeated: it can name anything.
            return refused(pom, "Maven's model builder stops on it");
        }
    }

    private List<Path> refused(Path pom, String why) {
        Place place = places.of(pom, 0);
        unknowns.add(new UnknownValue(place.file(), "refused: " + why, place));
        return List.of();
    }

    private ModelBuildingRequest request(Path pom) {
        var request = new DefaultModelBuildingRequest();
        request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        request.setProcessPlugins(false);
        // The first phase ends before imports, which bring no repository and no property.
        request.setTwoPhaseBuilding(true);
        request.setLocationTracking(true);
        // Nothing of the machine: a pom must not be able to put a secret from it into a URL.
        request.setSystemProperties(new Properties());
        request.setUserProperties(new Properties());
        request.setPomFile(pom.toFile());
        request.setModelSource(new FileModelSource(pom.toFile()));
        // The builder fills these in where they stand, and the next pom must see them as written.
        request.setProfiles(settings.activeProfiles().stream().map(Profile::clone).toList());
        request.setActiveProfileIds(settings.activeProfileIds());
        request.setModelResolver(new LocalRepositoryResolver(localRoots, places, unknowns::add));
        return request;
    }

    private void answerFailure(ModelBuildingException failure, Path pom) {
        unknowns.addAll(guard.takeOutside());
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
                                        + SafeText.of(places.withoutThePaths(problem.getMessage())),
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
        var properties = new PropertyPlaces(result, places, settings, settingsPlace);
        Model model = result.getEffectiveModel();
        answerProblems(result, pom);
        answerExtensions(model, result);
        Map<Spot, Repository> raws = rawRepositories(result);
        model.getRepositories()
                .forEach(
                        r -> answerRepository(r, RepositoryHost.Kind.REPOSITORY, raws, properties));
        model.getPluginRepositories()
                .forEach(
                        r ->
                                answerRepository(
                                        r,
                                        RepositoryHost.Kind.PLUGIN_REPOSITORY,
                                        raws,
                                        properties));
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
                                        + SafeText.of(places.withoutThePaths(problem.getMessage())),
                                places.of(
                                        problem.getSource(),
                                        Math.max(problem.getLineNumber(), 0))));
            }
        }
    }

    private void answerRepository(
            Repository repository,
            RepositoryHost.Kind kind,
            Map<Spot, Repository> raws,
            PropertyPlaces properties) {
        // Maven asks such a repository for nothing.
        if (isOff(repository.getReleases()) && isOff(repository.getSnapshots())) {
            return;
        }
        InputLocation at = repository.getLocation("url");
        Place place = places.of(at != null ? at : repository.getLocation(""));
        Repository raw = rawOf(repository, raws);
        String url = repository.getUrl() == null ? "" : repository.getUrl();
        if (repository.getId() == null && raw != null && raw.getId() != null) {
            unknowns.add(
                    new UnknownValue(
                            "repository " + SafeText.of(raw.getId()),
                            "its id could not be filled in, so no mirror can be selected for it",
                            place));
            return;
        }
        // Maven selects a mirror before it looks at the URL it replaces.
        Optional<UserSettings.Mirror> mirror =
                mirrors.of(
                        repository.getId() == null ? "default" : repository.getId(),
                        url,
                        repository.getLayout());
        if (mirror.isPresent()) {
            answerMirror(mirror.get());
            answerWhatTheMirrorAsks(mirror.get(), repository, url, place);
            return;
        }
        PropertyPlaces.Trace trace = properties.of(written(raw));
        boolean followed =
                raw != null
                        && trace.untraced().isEmpty()
                        && properties.reproduces(raw.getUrl(), repository.getUrl())
                        && properties.reproduces(raw.getId(), repository.getId());
        // While what fills it in is not followed, only the id as written may be shown.
        String id = SafeText.of(idToShow(repository, raw, followed));
        if (holdsExpression(url, place, id) || holdsUntraced(trace, place, id)) {
            return;
        }
        if (!followed) {
            unknowns.add(
                    new UnknownValue(
                            "repository " + id,
                            "what filled it in cannot be followed to whoever wrote it, so no host"
                                    + " is answered",
                            place));
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
                                                trace.uses(),
                                                server(id, place))));
    }

    private static boolean isOff(@Nullable RepositoryPolicy policy) {
        return policy != null && "false".equalsIgnoreCase(policy.getEnabled());
    }

    // A pattern other than everything can turn the build away, by what could not be filled in.
    private void answerWhatTheMirrorAsks(
            UserSettings.Mirror mirror, Repository repository, String url, Place place) {
        if (mirror.mirrorOf().equals("*")) {
            return;
        }
        String asked = repository.getId() + (mirror.mirrorOf().contains("external:") ? url : "");
        Matcher matcher = PropertyPlaces.EXPRESSION.matcher(asked);
        while (matcher.find()) {
            unknowns.add(
                    new UnknownValue(
                            "${" + SafeText.of(matcher.group(1)) + "}",
                            "whether a repository goes to mirror "
                                    + SafeText.of(mirror.id())
                                    + " depends on this, which nothing read here fills in",
                            place));
        }
    }

    private static String written(@Nullable Repository raw) {
        return raw == null ? "" : raw.getId() + " " + raw.getUrl();
    }

    private static String idToShow(
            Repository repository, @Nullable Repository raw, boolean followed) {
        if (followed && repository.getId() != null) {
            return repository.getId();
        }
        return raw == null || raw.getId() == null ? "default" : raw.getId();
    }

    private boolean holdsUntraced(PropertyPlaces.Trace trace, Place place, String id) {
        trace.untraced()
                .forEach(
                        name ->
                                unknowns.add(
                                        new UnknownValue(
                                                "${" + name + "}",
                                                "repository "
                                                        + id
                                                        + " is filled in from this, which depends"
                                                        + " on where the build runs or whose writer"
                                                        + " cannot be named",
                                                place)));
        return !trace.untraced().isEmpty();
    }

    private void answerMirror(UserSettings.Mirror mirror) {
        Place place = new Place(settingsPlace.file(), mirror.line(), settingsPlace.origin());
        String id = SafeText.of(mirror.id());
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
                                                List.of(),
                                                server(id, place))));
    }

    private boolean holdsExpression(String url, Place place, String id) {
        Matcher matcher = PropertyPlaces.EXPRESSION.matcher(url);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String name = SafeText.of(matcher.group(1));
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
                            "repository " + id + " is a local file URL and needs no host", place));
        } else {
            unknowns.add(
                    new UnknownValue(
                            "repository " + id, "its URL names no host that can be read", place));
        }
        return Optional.empty();
    }

    private ServerReference server(String id, Place repositoryPlace) {
        Integer line = settings.servers().get(id);
        return line == null
                ? new ServerReference(id, false, repositoryPlace)
                : new ServerReference(
                        id, true, new Place(settingsPlace.file(), line, settingsPlace.origin()));
    }

    // By its place: an id can be shared and can hold a property, and a model's own id too.
    private static @Nullable Repository rawOf(Repository repository, Map<Spot, Repository> raws) {
        String key = repository.getLocation("url") != null ? "url" : "";
        InputLocation at = repository.getLocation(key);
        return at == null ? null : raws.get(Spot.of(key, at));
    }

    // Once per pom: a pom can hold more repositories than asking each of them for each would bear.
    private Map<Spot, Repository> rawRepositories(ModelBuildingResult result) {
        Stream<Repository> inSettings =
                settings.activeProfiles().stream().flatMap(RepositoryHosts::repositoriesOf);
        Stream<Repository> inPoms =
                result.getModelIds().stream()
                        .map(result::getRawModel)
                        .filter(Objects::nonNull)
                        .flatMap(RepositoryHosts::allRepositories);
        Map<Spot, Repository> raws = new HashMap<>();
        Stream.concat(inSettings, inPoms)
                .forEach(
                        raw -> {
                            for (String key : List.of("url", "")) {
                                InputLocation at = raw.getLocation(key);
                                if (at != null && at.getSource() != null) {
                                    raws.putIfAbsent(Spot.of(key, at), raw);
                                }
                            }
                        });
        return raws;
    }

    private static Stream<Repository> repositoriesOf(Profile profile) {
        return Stream.concat(
                profile.getRepositories().stream(), profile.getPluginRepositories().stream());
    }

    private static Stream<Repository> allRepositories(Model raw) {
        return Stream.concat(
                Stream.concat(raw.getRepositories().stream(), raw.getPluginRepositories().stream()),
                raw.getProfiles().stream().flatMap(RepositoryHosts::repositoriesOf));
    }

    private void answerConditionalProfiles(ModelBuildingResult result, String modelId) {
        Model raw = result.getRawModel(modelId);
        if (raw == null) {
            return;
        }
        for (Profile profile : raw.getProfiles()) {
            String condition = conditionOf(profile.getActivation());
            if (condition != null && holdsSomething(profile)) {
                InputLocation id = profile.getLocation("id");
                Place place = places.of(id != null ? id : profile.getLocation(""));
                unknowns.add(
                        new UnknownValue(
                                "profile " + SafeText.of(profile.getId()),
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

    private static @Nullable String conditionOf(@Nullable Activation activation) {
        if (activation == null) {
            return null;
        }
        List<String> conditions = new ArrayList<>();
        if (activation.getJdk() != null) {
            conditions.add("JDK " + SafeText.of(activation.getJdk()));
        }
        if (activation.getOs() != null) {
            conditions.add("the operating system");
        }
        if (activation.getFile() != null) {
            conditions.add("a file");
        }
        if (activation.getProperty() != null) {
            conditions.add(
                    "the property "
                            + SafeText.of(String.valueOf(activation.getProperty().getName())));
        }
        return conditions.isEmpty() ? null : String.join(", ", conditions);
    }

    private List<Path> modulesOf(Model model, Path pom) {
        List<Path> modules = new ArrayList<>();
        Path directory = pom.toAbsolutePath().normalize().resolveSibling("");
        InputLocation lines = model.getLocation("modules");
        for (int i = 0; i < model.getModules().size(); i++) {
            String module = model.getModules().get(i);
            Path target = directory.resolve(module.replace('\\', File.separatorChar)).normalize();
            Path modulePom = Files.isDirectory(target) ? target.resolve("pom.xml") : target;
            if (places.isInTheCheckout(modulePom)) {
                modules.add(modulePom);
            } else {
                unknowns.add(
                        new UnknownValue(
                                "module " + SafeText.of(places.withoutThePaths(module)),
                                "it is missing, or lies outside the checkout, and is not read",
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

    /** Where a value stands, to tell one repository from another. */
    private record Spot(String key, @Nullable String source, int line, int column) {
        static Spot of(String key, InputLocation at) {
            String source = at.getSource() == null ? null : at.getSource().getLocation();
            return new Spot(key, source, at.getLineNumber(), at.getColumnNumber());
        }
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

    /** The builder with its one pom reader guarded and no activator that asks the machine. */
    private static final class GuardedFactory extends DefaultModelBuilderFactory {
        private final Places places;
        private @Nullable PomGuard guard;

        GuardedFactory(Places places) {
            this.places = places;
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
            return new BoundedInterpolator()
                    .setPathTranslator(newPathTranslator())
                    .setUrlNormalizer(newUrlNormalizer())
                    .setVersionPropertiesProcessor(newModelVersionPropertiesProcessor())
                    .setRootLocator(new CheckoutRoot(places.checkout()));
        }

        @Override
        protected ProfileActivator[] newProfileActivators() {
            return new ProfileActivator[0];
        }
    }
}
