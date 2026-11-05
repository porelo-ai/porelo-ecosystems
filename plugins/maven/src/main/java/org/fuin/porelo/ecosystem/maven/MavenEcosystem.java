package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.fuin.porelo.ecosystem.api.Detection;
import org.fuin.porelo.ecosystem.api.Ecosystem;
import org.fuin.porelo.ecosystem.api.EcosystemMain;
import org.fuin.porelo.ecosystem.api.EcosystemRefused;
import org.fuin.porelo.ecosystem.api.File;
import org.fuin.porelo.ecosystem.api.Finding;
import org.fuin.porelo.ecosystem.api.Repository;

/**
 * The Maven plugin behind Porelo's contract. Each host and credential has a key of its own, since
 * Porelo reads two values under one key as a disagreement.
 */
public final class MavenEcosystem implements Ecosystem {
    /** The name its executable is installed under. */
    public static final String NAME = "maven";

    /**
     * Runs the plugin as an executable.
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

    @Override
    public Detection detect(Path directory, Path home) {
        ToolNeeds needs = new ToolNeeds(directory.toAbsolutePath());
        ContainerUse containers = new ContainerUse();
        RepositoryHostsAnswer answer;
        try {
            answer =
                    RepositoryHosts.read(
                            directory,
                            home,
                            (model, places) -> {
                                needs.read(model, places);
                                containers.read(model, places);
                            });
        } catch (NotAMavenCheckoutException e) {
            throw new EcosystemRefused(EcosystemRefused.Reason.NOT_RECOGNIZED, "");
        } catch (IOException e) {
            throw new EcosystemRefused(
                    EcosystemRefused.Reason.UNREADABLE, String.valueOf(e.getMessage()));
        }
        List<Finding> findings = new ArrayList<>(needs.findings(answer.everyPomRead()));
        answer.hosts().forEach(host -> findings.addAll(ofHost(host)));
        answer.unknowns().forEach(unknown -> findings.add(ofUnknown(unknown)));
        return new Detection(
                findings.stream().distinct().toList(),
                Stream.concat(answer.unplaced().stream(), containers.unplaced().stream())
                        .map(MavenEcosystem::ofUnplaced)
                        .distinct()
                        .toList());
    }

    @Override
    public List<File> configure(List<Repository> repositories) {
        return MavenSettingsFile.of(repositories);
    }

    private static List<Finding> ofHost(RepositoryHost host) {
        ServerReference server = host.server();
        Finding asHost =
                Answers.rule(
                        "host." + host.host(),
                        host.host(),
                        host.place(),
                        noteOf(host) + (server.known() ? "" : ", with no credential named"));
        if (!server.known()) {
            return List.of(asHost);
        }
        return List.of(
                asHost,
                Answers.rule(
                        "credential." + server.id(),
                        server.id(),
                        server.place(),
                        "named by " + noteOf(host) + "; its value is not read"));
    }

    private static String noteOf(RepositoryHost host) {
        String what =
                switch (host.kind()) {
                    case REPOSITORY -> "repository ";
                    case PLUGIN_REPOSITORY -> "plugin repository ";
                    case MIRROR -> "mirror ";
                    case PROXY -> "proxy ";
                };
        return what + host.id() + " at " + host.url();
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
