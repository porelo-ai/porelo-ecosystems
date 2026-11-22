package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.fuin.porelo.ecosystem.shared.ToolDownload;
import org.fuin.porelo.ecosystem.shared.ToolUnavailable;
import org.fuin.porelo.ecosystem.shared.VendorFetch;

/** Finds the newest stable Maven a build allows, from Central or the mirror that takes it. */
final class MavenDownloads {
    /** Maven Central, where Maven is fetched from unless a mirror takes it. */
    static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    private static final String DIST = "org/apache/maven/apache-maven/";

    // A qualifier makes a pre-release, whatever the source calls it: 4.0.0-rc-7 is its "release".
    private static final Pattern STABLE = Pattern.compile("\\d+(\\.\\d+)*");

    private final VendorFetch fetch;

    MavenDownloads(VendorFetch fetch) {
        this.fetch = fetch;
    }

    /**
     * The newest stable Maven a build allows.
     *
     * @param asked A version range, a bare version as its floor, or nothing for any.
     * @throws ToolUnavailable when none fits
     */
    ToolDownload maven(String asked) {
        return maven(asked, CENTRAL);
    }

    /**
     * The newest stable Maven a build allows, from a repository holding Central's layout.
     *
     * @param asked A version range, a bare version as its floor, or nothing for any.
     * @param repository The repository's URL, ending in a slash.
     * @throws ToolUnavailable when none fits
     */
    ToolDownload maven(String asked, String repository) {
        VersionRange range = rangeOf(asked);
        String dist = repository + DIST;
        String version =
                versions(dist).stream()
                        .filter(v -> STABLE.matcher(v).matches())
                        .map(DefaultArtifactVersion::new)
                        .filter(range::containsVersion)
                        .max(Comparator.naturalOrder())
                        .map(DefaultArtifactVersion::toString)
                        .orElseThrow(() -> new ToolUnavailable("no stable Maven meets " + asked));
        String archive = dist + version + "/apache-maven-" + version + "-bin.tar.gz";
        String digest = fetch.answer(URI.create(archive + ".sha512")).strip().split("\\s+")[0];
        String mirror = repository.equals(CENTRAL) ? "" : URI.create(repository).getHost();
        return new ToolDownload(version, archive, digest, mirror == null ? "" : mirror);
    }

    private List<String> versions(String dist) {
        String metadata = fetch.answer(URI.create(dist + "maven-metadata.xml"));
        try {
            Xpp3Dom versioning = Xpp3DomBuilder.build(new StringReader(metadata));
            return Optional.ofNullable(versioning.getChild("versioning"))
                    .map(v -> v.getChild("versions"))
                    .map(Xpp3Dom::getChildren)
                    .stream()
                    .flatMap(java.util.Arrays::stream)
                    .map(Xpp3Dom::getValue)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (XmlPullParserException | IOException e) {
            throw new ToolUnavailable("Maven's list of versions could not be read");
        }
    }

    private static VersionRange rangeOf(String asked) {
        String written = asked.strip();
        boolean range = written.startsWith("[") || written.startsWith("(");
        if (!range && !written.isEmpty() && !STABLE.matcher(written).matches()) {
            throw new ToolUnavailable("no version range: " + asked);
        }
        try {
            return VersionRange.createFromVersionSpec(
                    written.isEmpty() ? "[0,)" : range ? written : "[" + written + ",)");
        } catch (InvalidVersionSpecificationException e) {
            throw new ToolUnavailable("no version range: " + asked);
        }
    }
}
