package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Stream;
import org.fuin.porelo.ecosystem.api.Detection;
import org.fuin.porelo.ecosystem.api.Ecosystem;
import org.fuin.porelo.ecosystem.api.EcosystemMain;
import org.fuin.porelo.ecosystem.api.EcosystemProtocol;
import org.fuin.porelo.ecosystem.api.EcosystemRefused;
import org.fuin.porelo.ecosystem.api.Extension;
import org.fuin.porelo.ecosystem.api.File;
import org.fuin.porelo.ecosystem.api.Finding;
import org.fuin.porelo.ecosystem.api.Repository;
import org.jspecify.annotations.Nullable;

/**
 * The Maven adapter behind Porelo's contract. Each host and credential has a key of its own, since
 * Porelo reads two values under one key as a disagreement.
 */
public final class MavenEcosystem implements Ecosystem {
    /** The name its executable is installed under. */
    public static final String NAME = "maven";

    private static final String VERSION_RESOURCE = "version.properties";

    private final Function<String, @Nullable String> environment;
    private final Function<Map<String, String>, ToolDownloads.Fetch> vendors;

    /** The adapter as it is installed: its environment holds what Porelo passed for the call. */
    public MavenEcosystem() {
        this(System::getenv, VendorHttp::new);
    }

    MavenEcosystem(
            Function<String, @Nullable String> environment,
            Function<Map<String, String>, ToolDownloads.Fetch> vendors) {
        this.environment = environment;
        this.vendors = vendors;
    }

    /**
     * Runs the adapter as an executable.
     *
     * @param arguments {@code describe}, or {@code serve <socket>}
     */
    public static void main(String[] arguments) {
        EcosystemMain.run(new MavenEcosystem(), arguments);
    }

    @Override
    public String ecosystem() {
        return NAME;
    }

    /**
     * Returns the version of the package this adapter was built in, which its build writes.
     *
     * @throws IllegalStateException When the build wrote none: an adapter that cannot say which
     *     build it is must not pass for one.
     */
    @Override
    public String version() {
        Properties written = new Properties();
        try (InputStream in = MavenEcosystem.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(VERSION_RESOURCE + " is not in the adapter");
            }
            written.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + VERSION_RESOURCE, e);
        }
        String version = written.getProperty("version", "");
        if (version.isBlank() || version.contains("${")) {
            throw new IllegalStateException(
                    VERSION_RESOURCE + " holds no version the build wrote: '" + version + "'");
        }
        return version;
    }

    @Override
    public Detection detect(Path directory, Path home) {
        ToolNeeds needs = new ToolNeeds(directory.toAbsolutePath());
        ContainerUse containers = new ContainerUse();
        ToolchainUse toolchains = new ToolchainUse();
        RepositoryHostsAnswer answer;
        try {
            answer =
                    RepositoryHosts.read(
                            directory,
                            home,
                            (model, places) -> {
                                needs.read(model, places);
                                containers.read(model, places);
                                toolchains.read(model, places);
                            });
        } catch (NotAMavenCheckoutException e) {
            throw new EcosystemRefused(EcosystemRefused.Reason.NOT_RECOGNIZED, "");
        } catch (IOException e) {
            throw new EcosystemRefused(
                    EcosystemRefused.Reason.UNREADABLE, String.valueOf(e.getMessage()));
        }
        List<Finding> findings = new ArrayList<>(needs.findings(answer.firstUnread()));
        answer.hosts().forEach(host -> findings.addAll(ofHost(host)));
        findings.addAll(ToolRepository.of(answer.hosts()));
        answer.unknowns().forEach(unknown -> findings.add(ofUnknown(unknown)));
        Path checkout = directory.toAbsolutePath();
        OwnSettings.of(checkout, MavenOptions.read(checkout)).ifPresent(findings::add);
        return new Detection(
                findings.stream().distinct().toList(),
                Stream.of(answer.unplaced(), containers.unplaced(), toolchains.unplaced())
                        .flatMap(List::stream)
                        .map(MavenEcosystem::ofUnplaced)
                        .distinct()
                        .toList());
    }

    @Override
    public Optional<List<Finding>> resolve(Path directory, Path home, List<Finding> findings) {
        Map<String, String> proxies = new LinkedHashMap<>();
        for (String name : EcosystemProtocol.PROXY_VARIABLES) {
            String value = environment.apply(name);
            if (value != null) {
                proxies.put(name, value);
            }
        }
        ToolDownloads.Fetch fetch;
        try {
            fetch = vendors.apply(proxies);
        } catch (ToolUnavailable e) {
            // Each tool then says this as why it was not found.
            fetch =
                    uri -> {
                        throw e;
                    };
        }
        try {
            return Optional.of(new ToolResolver(new ToolDownloads(fetch)).resolve(findings));
        } finally {
            if (fetch instanceof VendorHttp http) {
                http.close();
            }
        }
    }

    @Override
    public List<File> configure(List<Repository> repositories) {
        return MavenSettingsFile.of(repositories);
    }

    @Override
    public List<File> configure(List<Repository> repositories, Extension extension) {
        Map<String, String> mirrorOf = MavenExtension.mirrorOf(extension, repositories);
        if (MavenExtension.ownSettings(extension)) {
            return List.of();
        }
        return MavenSettingsFile.of(repositories, mirrorOf);
    }

    private static List<Finding> ofHost(RepositoryHost host) {
        ServerReference server = host.server();
        Finding asHost =
                Answers.rule(
                        "host." + host.host(),
                        host.host(),
                        host.place(),
                        noteOf(host)
                                + (server.known()
                                                || host.kind() == RepositoryHost.Kind.PLUGIN_REACHES
                                        ? ""
                                        : ", with no credential named"));
        if (!server.known()) {
            return List.of(asHost);
        }
        Finding named =
                Answers.rule(
                        "credential." + server.id(),
                        server.id(),
                        server.place(),
                        "named by " + noteOf(host) + "; its value is not read");
        Credential sent = server.credential();
        if (sent == null) {
            return List.of(asHost, named);
        }
        return List.of(
                asHost,
                named,
                Answers.rule(
                        "credential-kind." + server.id(),
                        sent.kind(),
                        server.place(),
                        sent.toStore()));
    }

    private static String noteOf(RepositoryHost host) {
        String what =
                switch (host.kind()) {
                    case REPOSITORY -> "repository ";
                    case PLUGIN_REPOSITORY -> "plugin repository ";
                    case MIRROR -> "mirror ";
                    case PLUGIN_REACHES -> "the plugin ";
                    case PROXY -> "proxy ";
                };
        // A checkout can move the super POM's Central by a property and still leave it no file.
        boolean builtIn =
                host.place().file().isEmpty()
                        && (host.kind() == RepositoryHost.Kind.REPOSITORY
                                || host.kind() == RepositoryHost.Kind.PLUGIN_REPOSITORY)
                        && withoutEndSlash(host.url())
                                .equals(withoutEndSlash(ToolDownloads.CENTRAL));
        return (builtIn ? "Maven's built-in " : "") + what + host.id() + " at " + host.url();
    }

    private static String withoutEndSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Finding ofUnknown(UnknownValue unknown) {
        return Answers.unknown(
                Finding.Part.RULES, "unknown." + unknown.what(), unknown.place(), unknown.why());
    }

    private static org.fuin.porelo.ecosystem.api.Unplaced ofUnplaced(Unplaced unplaced) {
        Place place = unplaced.place();
        return new org.fuin.porelo.ecosystem.api.Unplaced(
                place.file().isEmpty() ? "pom.xml" : place.file(), place.line(), unplaced.note());
    }
}
