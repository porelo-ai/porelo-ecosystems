package org.fuin.porelo.ecosystem.maven;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jspecify.annotations.Nullable;

/** What a plugin the build runs reaches of its own, beside the repositories. */
final class PluginHosts {
    private static final String JAVADOC = "org.apache.maven.plugins:maven-javadoc-plugin";
    private static final String PGPVERIFY = "org.simplify4u.plugins:pgpverify-maven-plugin";
    // As pgpverify-maven-plugin 1.19.1's own plugin.xml names them.
    private static final String PGP_DEFAULTS =
            "hkps://keyserver.ubuntu.com, hkps://keys.openpgp.org, hkps://pgp.mit.edu";

    /** One address a plugin reaches, with where it stands. */
    record Reached(String url, @Nullable InputLocation location) {}

    private PluginHosts() {}

    /**
     * Reads what this plugin reaches.
     *
     * @param plugin A plugin the build runs.
     * @param managed Its build's pluginManagement, which the model read holds apart.
     */
    static List<Reached> of(Plugin plugin, @Nullable PluginManagement managed) {
        return switch (plugin.getKey()) {
            case JAVADOC -> javadocLinks(plugin, managed);
            case PGPVERIFY -> keyServers(plugin, managed);
            default -> List.of();
        };
    }

    private static List<Reached> javadocLinks(Plugin plugin, @Nullable PluginManagement managed) {
        List<Reached> stated = links(plugin);
        return stated.isEmpty() && managed != null
                ? managed.getPlugins().stream()
                        .filter(other -> other.getKey().equals(plugin.getKey()))
                        .flatMap(other -> links(other).stream())
                        .toList()
                : stated;
    }

    private static List<Reached> links(Plugin plugin) {
        return ToolNeeds.settings(plugin)
                .map(configuration -> configuration.getChild("links"))
                .filter(links -> links != null)
                .flatMap(links -> Arrays.stream(links.getChildren("link")))
                .filter(link -> link.getValue() != null && reachesAHost(link.getValue()))
                .map(link -> new Reached(link.getValue().strip(), ToolNeeds.location(link)))
                .toList();
    }

    // A link may be a directory, which javadoc reads from disk.
    private static boolean reachesAHost(String link) {
        String written = link.strip().toLowerCase(Locale.ROOT);
        return written.contains("${") || (written.contains("://") && !written.startsWith("file:"));
    }

    private static List<Reached> keyServers(Plugin plugin, @Nullable PluginManagement managed) {
        List<Reached> stated = servers(plugin);
        if (stated.isEmpty() && managed != null) {
            stated =
                    managed.getPlugins().stream()
                            .filter(other -> other.getKey().equals(plugin.getKey()))
                            .flatMap(other -> servers(other).stream())
                            .toList();
        }
        return stated.isEmpty() ? split(PGP_DEFAULTS, plugin.getLocation("")) : stated;
    }

    private static List<Reached> servers(Plugin plugin) {
        return ToolNeeds.settings(plugin)
                .map(configuration -> configuration.getChild("pgpKeyServer"))
                .filter(server -> server != null && server.getValue() != null)
                .flatMap(
                        (Xpp3Dom server) ->
                                split(server.getValue(), ToolNeeds.location(server)).stream())
                .toList();
    }

    private static List<Reached> split(String servers, @Nullable InputLocation location) {
        return Arrays.stream(servers.split(","))
                .map(String::strip)
                .filter(server -> !server.isEmpty())
                .map(server -> new Reached(server, location))
                .toList();
    }
}
