package org.fuin.porelo.ecosystem.maven;

/** The image lines that install one fetched tool, ordinary lines a person can read and change. */
final class ToolLines {
    private ToolLines() {}

    /** A tool whose commands go on the path, over what stands there of the same name. */
    static String install(String name, ToolDownload tool) {
        return fetched(name, tool) + " ln -sf /opt/" + name + "/bin/* /usr/local/bin/";
    }

    /** A tool beside another: only what it adds goes on the path. */
    static String installBeside(String name, ToolDownload tool) {
        return fetched(name, tool)
                + " for f in /opt/"
                + name
                + "/bin/*; do [ -e /usr/local/bin/\"${f##*/}\" ] || [ -L /usr/local/bin/\"${f##*/}\" ]"
                + " || ln -s \"$f\" /usr/local/bin/;"
                + " done";
    }

    private static String fetched(String name, ToolDownload tool) {
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
