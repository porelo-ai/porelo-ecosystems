package org.fuin.porelo.ecosystem.shared;

import java.util.regex.Pattern;

/** The image lines that install one fetched tool, ordinary lines a person can read and change. */
public final class ToolLines {
    // The name stands unquoted in paths of lines that run as root.
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    private ToolLines() {}

    /**
     * A tool whose commands go on the path, over what stands there of the same name.
     *
     * @param name the folder under {@code /opt} it goes into
     * @param tool its archive
     * @return the image line
     * @throws IllegalArgumentException when the name is no plain folder name
     */
    public static String install(String name, ToolDownload tool) {
        return fetched(name, tool) + " ln -sf /opt/" + name + "/bin/* /usr/local/bin/";
    }

    /**
     * A tool beside another: only what it adds goes on the path.
     *
     * @param name the folder under {@code /opt} it goes into
     * @param tool its archive
     * @return the image line
     * @throws IllegalArgumentException when the name is no plain folder name
     */
    public static String installBeside(String name, ToolDownload tool) {
        return fetched(name, tool)
                + " for f in /opt/"
                + name
                + "/bin/*; do [ -e /usr/local/bin/\"${f##*/}\" ] || [ -L /usr/local/bin/\"${f##*/}\" ]"
                + " || ln -s \"$f\" /usr/local/bin/;"
                + " done";
    }

    private static String fetched(String name, ToolDownload tool) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("no plain folder name for a tool: " + name);
        }
        String archive = "/tmp/" + name + ".tgz";
        return "RUN set -eu; mkdir -p /opt/%s; curl -fsSL -o %s '%s'; echo '%s  %s' | %s -c -;"
                        .formatted(
                                name,
                                archive,
                                tool.url(),
                                tool.digest(),
                                archive,
                                tool.checkCommand())
                + " tar xzf %s -C /opt/%s --strip-components=1; rm %s;"
                        .formatted(archive, name, archive);
    }
}
