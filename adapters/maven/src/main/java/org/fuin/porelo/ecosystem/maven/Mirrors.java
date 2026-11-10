package org.fuin.porelo.ecosystem.maven;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Selects the mirror Maven would, step for step as its {@code DefaultMirrorSelector}: an exact id
 * first, then the first pattern that matches, each only for a layout it names.
 */
final class Mirrors {
    private static final String ANY = "*";
    private static final String ANY_EXTERNAL = "external:*";
    private static final String ANY_EXTERNAL_HTTP = "external:http:*";
    private static final Set<String> HTTP_PROTOCOLS = Set.of("http", "dav", "dav:http", "dav+http");
    // How Maven's RemoteRepository takes a URL apart: what it calls local decides a mirror.
    private static final Pattern URL =
            Pattern.compile("([^:/]+(:[^:/]{2,}+(?=://))?):(//([^@/]*@)?([^/:]+))?.*");

    private final List<UserSettings.Mirror> mirrors;

    Mirrors(List<UserSettings.Mirror> mirrors) {
        this.mirrors = List.copyOf(mirrors);
    }

    Optional<UserSettings.Mirror> of(String repositoryId, String url, String layout) {
        for (UserSettings.Mirror mirror : mirrors) {
            if (repositoryId.equals(mirror.mirrorOf()) && matchesLayout(layout, mirror.layouts())) {
                return Optional.of(mirror);
            }
        }
        return mirrors.stream()
                .filter(
                        mirror ->
                                matchesPattern(mirror.mirrorOf(), repositoryId, url)
                                        && matchesLayout(layout, mirror.layouts()))
                .findFirst();
    }

    private static boolean matchesPattern(String pattern, String repositoryId, String url) {
        if (ANY.equals(pattern) || pattern.equals(repositoryId)) {
            return true;
        }
        boolean matched = false;
        for (String part : pattern.split(",")) {
            if (part.length() > 1 && part.startsWith("!")) {
                if (part.substring(1).equals(repositoryId)) {
                    return false;
                }
            } else if (part.equals(repositoryId)) {
                return true;
            } else if (ANY.equals(part)
                    || (ANY_EXTERNAL.equals(part) && isExternal(url))
                    || (ANY_EXTERNAL_HTTP.equals(part) && isExternalHttp(url))) {
                matched = true;
            }
        }
        return matched;
    }

    private static boolean matchesLayout(String layout, String layouts) {
        if (layouts.isEmpty() || ANY.equals(layouts) || layouts.equals(layout)) {
            return true;
        }
        boolean matched = false;
        for (String part : layouts.split(",")) {
            if (part.length() > 1 && part.startsWith("!")) {
                if (part.substring(1).equals(layout)) {
                    return false;
                }
            } else if (part.equals(layout)) {
                return true;
            } else if (ANY.equals(part)) {
                matched = true;
            }
        }
        return matched;
    }

    private static boolean isExternal(String url) {
        return !isLocal(hostOf(url)) && !"file".equalsIgnoreCase(protocolOf(url));
    }

    private static boolean isExternalHttp(String url) {
        return HTTP_PROTOCOLS.contains(protocolOf(url).toLowerCase(java.util.Locale.ROOT))
                && !isLocal(hostOf(url));
    }

    private static boolean isLocal(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host);
    }

    private static String protocolOf(String url) {
        Matcher matcher = URL.matcher(url);
        return matcher.matches() ? matcher.group(1) : "";
    }

    private static String hostOf(String url) {
        Matcher matcher = URL.matcher(url);
        if (!matcher.matches() || matcher.group(5) == null) {
            return "";
        }
        return matcher.group(5);
    }
}
