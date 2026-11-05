package org.fuin.porelo.ecosystem.maven;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.maven.model.Build;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.fuin.porelo.ecosystem.api.Finding;
import org.jspecify.annotations.Nullable;

/** What a task's image must hold and how much memory the build asks, from the effective models. */
final class ToolNeeds {
    private static final String WRAPPER = ".mvn/wrapper/maven-wrapper.properties";
    private static final String JVM_CONFIG = ".mvn/jvm.config";
    private static final String ONLY_SCRIPT = "distributionType=only-script";
    private static final String DIGEST = "distributionSha256Sum=";
    private static final Place NOTHING_ASKED = new Place("pom.xml", 0, Origin.CHECKOUT);
    private static final String COMPILER = "org.apache.maven.plugins:maven-compiler-plugin";
    private static final String ENFORCER = "org.apache.maven.plugins:maven-enforcer-plugin";
    private static final String EXEC = "org.codehaus.mojo:exec-maven-plugin";
    private static final List<String> TEST_RUNNERS =
            List.of(
                    "org.apache.maven.plugins:maven-surefire-plugin",
                    "org.apache.maven.plugins:maven-failsafe-plugin");
    // What a plugin runs from the system, as far as is known without running it.
    private static final Map<String, List<String>> PACKAGES =
            Map.of(
                    "org.graalvm.buildtools:native-maven-plugin", List.of("gcc", "zlib1g-dev"),
                    "org.apache.maven.plugins:maven-gpg-plugin", List.of("gnupg"),
                    "io.github.git-commit-id:git-commit-id-maven-plugin", List.of("git"),
                    "pl.project13.maven:git-commit-id-plugin", List.of("git"),
                    "org.codehaus.mojo:buildnumber-maven-plugin", List.of("git"),
                    "io.fabric8:docker-maven-plugin", List.of("docker"),
                    "com.spotify:dockerfile-maven-plugin", List.of("docker"));
    // The JDKs Ubuntu 24.04, Porelo's default image, packages, as measured; a JDK compiles older
    // releases.
    private static final List<Integer> PACKAGED_JDKS = List.of(8, 11, 17, 21, 25);
    private static final Pattern HEAP = Pattern.compile("-Xmx(\\d+)([kKmMgG]?)");
    private static final Pattern WRAPPED_MAVEN =
            Pattern.compile("apache-maven-(\\d[^/]*?)-bin\\.(?:zip|tar\\.gz)");

    private final Path checkout;
    private @Nullable Finding java;
    private int highestJava;
    private final List<JavaNeed> javaNeeds = new ArrayList<>();
    private @Nullable Finding javaUnknown;
    private @Nullable Finding maven;
    private boolean wrapperRuns;
    private boolean compilerUnpinned;
    private @Nullable Finding testMemory;
    private long highestTestMemory;
    private final Map<String, Finding> packages = new LinkedHashMap<>();
    private final List<Finding> fromFiles = new ArrayList<>();
    private final List<Finding> wrapperPackages = new ArrayList<>();

    ToolNeeds(Path checkout) {
        this.checkout = checkout;
        readWrapper();
        readJvmConfig();
    }

    /** Takes in one pom's effective model. */
    void read(Model model, Places places) {
        compilerUnpinned |= !pinsTheCompiler(model);
        readJava(model, places);
        readMaven(model, places);
        readPackages(model, places);
        readTestMemory(model, places);
    }

    /**
     * What was found, with Maven's version always named: a pom means a Maven.
     *
     * @param everyPomRead false when what asks for a version may stand in a pom that went unread
     */
    List<Finding> findings(boolean everyPomRead) {
        List<Finding> all = new ArrayList<>();
        if (java != null) {
            all.add(java);
            all.add(jdkPackage(java, javaNeeds));
        } else if (javaUnknown != null) {
            all.add(javaUnknown);
        } else if (!everyPomRead) {
            all.add(
                    Answers.unknown(
                            Finding.Part.TOOLS,
                            "java.version",
                            NOTHING_ASKED,
                            "no pom read asks for a Java release, and one that went unread may"));
        } else {
            all.add(
                    Answers.tool(
                            "package.default-jdk-headless",
                            "default-jdk-headless",
                            NOTHING_ASKED,
                            "Maven runs on a JDK, and no Java release is asked for"));
        }
        Finding mavenAsked =
                maven != null
                        ? maven
                        : Answers.tool(
                                "maven.version",
                                "",
                                NOTHING_ASKED,
                                everyPomRead
                                        ? "no version is asked for, so any Maven serves"
                                        : "no pom read asks for a version, and one that went"
                                                + " unread may");
        all.add(mavenAsked);
        if (wrapperRuns) {
            all.addAll(wrapperPackages);
        } else {
            all.add(
                    beside(
                            mavenAsked,
                            "maven",
                            false,
                            "no wrapper runs the build, so the image's Maven does"
                                    + (mavenAsked.value().isEmpty()
                                            ? ""
                                            : ", which may not be the "
                                                    + mavenAsked.value()
                                                    + " asked for")
                                    + (compilerUnpinned
                                            ? "; a pom pins no maven-compiler-plugin, so that"
                                                    + " Maven's default compiles, and an old one"
                                                    + " can refuse the JDK"
                                            : "")));
        }
        all.addAll(packages.values());
        all.addAll(fromFiles);
        if (testMemory != null) {
            all.add(testMemory);
        }
        return all;
    }

    private static Finding jdkPackage(Finding java, List<JavaNeed> needs) {
        for (int packaged : PACKAGED_JDKS) {
            if (needs.stream().allMatch(need -> need.allows(packaged))) {
                return beside(
                        java,
                        "openjdk-" + packaged + "-jdk-headless",
                        false,
                        "the lowest JDK packaged that meets " + statedBy(needs));
            }
        }
        return beside(
                java,
                "openjdk-" + java.value() + "-jdk-headless",
                true,
                "no JDK known to be packaged meets " + statedBy(needs));
    }

    private static String statedBy(List<JavaNeed> needs) {
        return needs.stream().map(JavaNeed::written).distinct().collect(joining(" and "));
    }

    // The package stands where what asks for it stands, so a person reads both in one place.
    private static Finding beside(Finding asked, String name, boolean unknown, String note) {
        return new Finding(
                Finding.Part.TOOLS,
                "package." + name,
                unknown ? "" : name,
                asked.file(),
                asked.line(),
                asked.origin(),
                unknown,
                note);
    }

    private void readWrapper() {
        Path file = checkout.resolve(WRAPPER);
        List<String> lines = lines(file);
        wrapperRuns = Files.isRegularFile(checkout.resolve("mvnw"));
        boolean onlyScript = lines.stream().anyMatch(l -> l.strip().equals(ONLY_SCRIPT));
        boolean digest = lines.stream().anyMatch(l -> l.strip().startsWith(DIGEST));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (!line.startsWith("distributionUrl=")) {
                continue;
            }
            // A properties file writes the colon of a URL with a backslash in front of it.
            String url = line.substring("distributionUrl=".length()).replace("\\", "");
            Place place = new Place(WRAPPER, i + 1, Origin.CHECKOUT);
            if (onlyScript && digest && url.endsWith(".zip")) {
                wrapperPackages.add(
                        Answers.tool(
                                "package.unzip",
                                "unzip",
                                place,
                                "without it the wrapper fetches a tarball, which fails the digest"
                                        + " of this zip"));
            }
            Matcher version = WRAPPED_MAVEN.matcher(url);
            if (version.find()) {
                maven =
                        Answers.tool(
                                "maven.version",
                                version.group(1),
                                place,
                                "the wrapper fetches this version");
            }
            HostUrl.parse(url)
                    .ifPresent(
                            host ->
                                    fromFiles.add(
                                            Answers.rule(
                                                    "host." + host.host(),
                                                    host.host(),
                                                    place,
                                                    "the Maven wrapper fetches Maven from "
                                                            + host.url())));
        }
    }

    private void readJvmConfig() {
        List<String> lines = lines(checkout.resolve(JVM_CONFIG));
        for (int i = 0; i < lines.size(); i++) {
            Matcher heap = HEAP.matcher(lines.get(i));
            if (heap.find()) {
                fromFiles.add(
                        Answers.rule(
                                "memory.maven",
                                heap.group(1) + heap.group(2),
                                new Place(JVM_CONFIG, i + 1, Origin.CHECKOUT),
                                "the heap Maven itself runs with"));
            }
        }
    }

    private static List<String> lines(Path file) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }
    }

    // The compiler's level names only the bytecode, so a build's enforcer can ask for a higher JDK.
    private void readJava(Model model, Places places) {
        readJavaLevel(model, places);
        for (Xpp3Dom configuration : configurations(model, ENFORCER)) {
            Xpp3Dom version = rule(configuration, "requireJavaVersion");
            if (version != null && version.getValue() != null) {
                takeJava(version.getValue(), places.of(location(version)), "the enforcer's rule");
            }
        }
    }

    private void readJavaLevel(Model model, Places places) {
        boolean found = false;
        for (Xpp3Dom configuration : configurations(model, COMPILER)) {
            for (String name : List.of("release", "target", "source")) {
                Xpp3Dom value = configuration.getChild(name);
                if (value != null && value.getValue() != null) {
                    takeJava(
                            value.getValue(), places.of(location(value)), "the compiler's " + name);
                    found = true;
                }
            }
        }
        if (found) {
            return;
        }
        for (String name :
                List.of(
                        "maven.compiler.release",
                        "maven.compiler.target",
                        "maven.compiler.source",
                        "java.version")) {
            String value = model.getProperties().getProperty(name);
            if (value != null) {
                takeJava(value, propertyPlace(model, name, places), "the property " + name);
                return;
            }
        }
    }

    // The highest of all modules: that JDK builds them all, and a lower one does not.
    private void takeJava(String written, Place place, String source) {
        if (written.contains("${")) {
            javaUnknown =
                    Answers.unknown(
                            Finding.Part.TOOLS,
                            "java.version",
                            place,
                            source + " is written as " + written + ", which nothing read fills in");
            return;
        }
        JavaNeed.of(written)
                .ifPresent(
                        need -> {
                            javaNeeds.add(need);
                            int lowest = need.lowest() == 0 ? PACKAGED_JDKS.get(0) : need.lowest();
                            if (lowest > highestJava) {
                                highestJava = lowest;
                                java =
                                        Answers.tool(
                                                "java.version",
                                                Integer.toString(lowest),
                                                place,
                                                "from " + source);
                            }
                        });
    }

    private void readMaven(Model model, Places places) {
        if (maven != null) {
            return;
        }
        for (Xpp3Dom configuration : configurations(model, ENFORCER)) {
            Xpp3Dom version = rule(configuration, "requireMavenVersion");
            if (version != null && version.getValue() != null) {
                maven =
                        Answers.tool(
                                "maven.version",
                                version.getValue(),
                                places.of(location(version)),
                                "the range the enforcer's rule asks for");
                return;
            }
        }
        if (model.getPrerequisites() != null && model.getPrerequisites().getMaven() != null) {
            maven =
                    Answers.tool(
                            "maven.version",
                            model.getPrerequisites().getMaven(),
                            places.of(model.getPrerequisites().getLocation("maven")),
                            "the lowest the pom's prerequisites name");
        }
    }

    private void readPackages(Model model, Places places) {
        for (Plugin plugin : used(model)) {
            for (String name : PACKAGES.getOrDefault(plugin.getKey(), List.of())) {
                packages.putIfAbsent(
                        name,
                        Answers.tool(
                                "package." + name,
                                name,
                                places.of(plugin.getLocation("")),
                                plugin.getArtifactId() + " runs it"));
            }
        }
        for (Xpp3Dom configuration : configurations(model, EXEC)) {
            Xpp3Dom executable = configuration.getChild("executable");
            if (executable != null && executable.getValue() != null) {
                takeExecutable(executable.getValue().strip(), places.of(location(executable)));
            }
        }
    }

    private void takeExecutable(String written, Place place) {
        // A JDK's own is named already, as this image's JDK.
        if (written.startsWith("${java.home}")) {
            return;
        }
        if (written.contains("${")) {
            String key = "package." + written;
            packages.putIfAbsent(
                    key,
                    Answers.unknown(
                            Finding.Part.TOOLS,
                            key,
                            place,
                            "exec-maven-plugin runs this, which nothing read fills in"));
            return;
        }
        // In the checkout it needs no package, and a JDK's or Maven's is named already.
        if (written.startsWith(".") || written.equals("java") || written.equals("mvn")) {
            return;
        }
        String name = written.substring(written.lastIndexOf('/') + 1);
        packages.putIfAbsent(
                name,
                Answers.tool(
                        "package." + name,
                        name,
                        place,
                        "exec-maven-plugin runs it; the package holding it may be named otherwise"));
    }

    private void readTestMemory(Model model, Places places) {
        for (String runner : TEST_RUNNERS) {
            for (Xpp3Dom configuration : configurations(model, runner)) {
                Xpp3Dom line = configuration.getChild("argLine");
                if (line != null && line.getValue() != null) {
                    takeTestMemory(line.getValue(), places.of(location(line)));
                }
            }
        }
        String property = model.getProperties().getProperty("argLine");
        if (property != null) {
            takeTestMemory(property, propertyPlace(model, "argLine", places));
        }
    }

    private void takeTestMemory(String line, Place place) {
        Matcher heap = HEAP.matcher(line);
        if (heap.find()) {
            long bytes = bytes(heap.group(1), heap.group(2));
            if (bytes > highestTestMemory) {
                highestTestMemory = bytes;
                testMemory =
                        Answers.rule(
                                "memory.tests",
                                heap.group(1) + heap.group(2),
                                place,
                                "the heap the tests run with");
            }
        }
    }

    private static long bytes(String amount, String unit) {
        long value = Long.parseLong(amount);
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "k" -> value << 10;
            case "m" -> value << 20;
            case "g" -> value << 30;
            default -> value;
        };
    }

    private static Place propertyPlace(Model model, String name, Places places) {
        InputLocation properties = model.getLocation("properties");
        InputLocation property = properties == null ? null : properties.getLocation(name);
        return places.of(property != null ? property : properties);
    }

    // A plugin's configuration, its executions', and what its management gives it, this first.
    private static List<Xpp3Dom> configurations(Model model, String key) {
        Build build = model.getBuild();
        if (build == null) {
            return List.of();
        }
        Optional<Plugin> used =
                used(model).stream().filter(p -> p.getKey().equals(key)).findFirst();
        Stream<Plugin> managed =
                build.getPluginManagement() == null
                        ? Stream.empty()
                        : build.getPluginManagement().getPlugins().stream()
                                .filter(p -> p.getKey().equals(key));
        boolean mayBeUnlisted = key.equals(COMPILER) || TEST_RUNNERS.contains(key);
        if (used.isEmpty() && !mayBeUnlisted) {
            return List.of();
        }
        return Stream.concat(used.stream(), managed)
                .flatMap(
                        plugin ->
                                Stream.concat(
                                        Stream.of(plugin.getConfiguration()),
                                        plugin.getExecutions().stream()
                                                .map(PluginExecution::getConfiguration)))
                .filter(Xpp3Dom.class::isInstance)
                .map(Xpp3Dom.class::cast)
                .toList();
    }

    private static boolean pinsTheCompiler(Model model) {
        Build build = model.getBuild();
        if (build == null) {
            return false;
        }
        Stream<Plugin> managed =
                build.getPluginManagement() == null
                        ? Stream.empty()
                        : build.getPluginManagement().getPlugins().stream();
        return Stream.concat(used(model).stream(), managed)
                .anyMatch(p -> p.getKey().equals(COMPILER) && p.getVersion() != null);
    }

    private static List<Plugin> used(Model model) {
        return model.getBuild() == null ? List.of() : model.getBuild().getPlugins();
    }

    private static @Nullable Xpp3Dom rule(Xpp3Dom configuration, String name) {
        Xpp3Dom rules = configuration.getChild("rules");
        Xpp3Dom rule = rules == null ? null : rules.getChild(name);
        return rule == null ? null : rule.getChild("version");
    }

    private static @Nullable InputLocation location(Xpp3Dom element) {
        return element.getInputLocation() instanceof InputLocation location
                ? Objects.requireNonNull(location)
                : null;
    }
}
