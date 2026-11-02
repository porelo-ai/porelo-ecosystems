package org.fuin.porelo.ecosystem.maven;

import java.util.List;
import org.fuin.porelo.ecosystem.api.File;
import org.fuin.porelo.ecosystem.api.Repository;

/** The settings a task's Maven needs so that it reaches its repositories through the broker. */
final class MavenSettingsFile {
    private static final String PATH = ".m2/settings.xml";
    private static final String HEAD =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <settings>
              <mirrors>
            """;
    private static final String MIRROR =
            """
                <mirror>
                  <id>%1$s</id>
                  <mirrorOf>%1$s</mirrorOf>
                  <url>%2$s</url>
                </mirror>
            """;
    private static final String PROFILE_HEAD =
            """
              </mirrors>
              <profiles>
                <profile>
                  <id>porelo-repositories</id>
                  <activation><activeByDefault>true</activeByDefault></activation>
            """;
    private static final String REPOSITORY =
            """
                    <%1$s>
                      <id>%2$s</id>
                      <url>%3$s</url>
                      <releases><enabled>true</enabled></releases>
                      <snapshots><enabled>true</enabled></snapshots>
                    </%1$s>
            """;
    private static final String TAIL =
            """
                </profile>
              </profiles>
            </settings>
            """;

    private MavenSettingsFile() {}

    /** No credential stands in it: the broker adds that, so the file needs no guarding. */
    static List<File> of(List<Repository> repositories) {
        if (repositories.isEmpty()) {
            return List.of();
        }
        StringBuilder xml = new StringBuilder(HEAD);
        repositories.forEach(
                r -> xml.append(MIRROR.formatted(escaped(r.name()), escaped(r.url()))));
        xml.append(PROFILE_HEAD);
        section(xml, repositories, "repositories", "repository");
        section(xml, repositories, "pluginRepositories", "pluginRepository");
        xml.append(TAIL);
        return List.of(new File(PATH, xml.toString(), false));
    }

    private static void section(
            StringBuilder xml, List<Repository> repositories, String list, String element) {
        xml.append("      <").append(list).append(">\n");
        repositories.forEach(
                r ->
                        xml.append(
                                REPOSITORY.formatted(
                                        element, escaped(r.name()), escaped(r.url()))));
        xml.append("      </").append(list).append(">\n");
    }

    private static String escaped(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
