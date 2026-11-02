package org.fuin.porelo.ecosystem.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;

/** Finds a parent by the local repository's layout, and nowhere else. */
final class LocalRepositoryResolver implements ModelResolver {
    // A forge writes coordinates, and they become a path.
    private static final Pattern COORDINATE = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]*");

    private final List<Path> roots;
    private final Places places;
    private final Consumer<UnknownValue> unknowns;

    LocalRepositoryResolver(List<Path> roots, Places places, Consumer<UnknownValue> unknowns) {
        this.roots = List.copyOf(roots);
        this.places = places;
        this.unknowns = unknowns;
    }

    @Override
    public ModelSource resolveModel(Parent parent) {
        return find(
                parent.getGroupId(),
                parent.getArtifactId(),
                parent.getVersion(),
                parent.getLocation("artifactId"));
    }

    @Override
    public ModelSource resolveModel(String groupId, String artifactId, String version)
            throws UnresolvableModelException {
        throw new UnresolvableModelException(
                "only a parent is resolved", groupId, artifactId, version);
    }

    // Imports are never read: the first phase of the build stops before them.
    @Override
    public ModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
        return resolveModel(
                dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    @Override
    public void addRepository(Repository repository) {
        // Nothing is ever fetched, so no repository is ever asked.
    }

    @Override
    public void addRepository(Repository repository, boolean replace) {
        // Nothing is ever fetched, so no repository is ever asked.
    }

    @Override
    public ModelResolver newCopy() {
        return this;
    }

    private ModelSource find(
            String groupId, String artifactId, String version, InputLocation askedAt) {
        String coordinates = SafeText.of(groupId + ":" + artifactId + ":" + version);
        if (!isCoordinate(artifactId) || !isCoordinate(version) || !isCoordinate(groupId)) {
            unknowns.accept(
                    new UnknownValue(
                            coordinates,
                            "these coordinates cannot be looked up in a local repository",
                            places.of(askedAt)));
            return new MissingPomSource(groupId, artifactId, version);
        }
        for (Path root : roots) {
            Path pom =
                    root.resolve(groupId.replace('.', '/'))
                            .resolve(artifactId)
                            .resolve(version)
                            .resolve(artifactId + "-" + version + ".pom");
            if (Files.isRegularFile(pom) && places.mayRead(pom)) {
                return new FileModelSource(pom.toFile());
            }
        }
        unknowns.accept(
                new UnknownValue(
                        coordinates,
                        "this parent is not in the local repository; building the project once"
                                + " fills it",
                        places.of(askedAt)));
        return new MissingPomSource(groupId, artifactId, version);
    }

    private static boolean isCoordinate(String value) {
        return COORDINATE.matcher(value).matches() && !value.contains("..");
    }
}
