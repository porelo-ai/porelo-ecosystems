package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Harm prevented: a merge read differently from Maven's sends the person's egress to wrong hosts.
 */
class RealMavenComparisonTest {
    @TempDir Path root;

    @FunctionalInterface
    interface Build {
        void on(Fixture fixture) throws IOException;
    }

    static Stream<Arguments> merges() {
        return Stream.of(
                Arguments.of(
                        "overridden in the parent",
                        (Build) MergeFixtures::propertyOverriddenInTheParentUsedInTheChild),
                Arguments.of(
                        "repository above, property below",
                        (Build) MergeFixtures::repositoryInTheGrandparentPropertyInTheChild),
                Arguments.of(
                        "project fields and revision",
                        (Build) MergeFixtures::projectFieldsAndRevision),
                Arguments.of("nested properties", (Build) MergeFixtures::nestedProperties),
                Arguments.of(
                        "property from a settings profile",
                        (Build) MergeFixtures::propertyFromASettingsProfile),
                Arguments.of(
                        "repository in a settings profile",
                        (Build) MergeFixtures::repositoryInASettingsProfile),
                Arguments.of(
                        "profile active by default", (Build) MergeFixtures::activeByDefaultProfile),
                Arguments.of("BOM with repositories", (Build) MergeFixtures::bomWithRepositories),
                Arguments.of(
                        "the later of two settings profiles",
                        (Build) MergeFixtures::laterSettingsProfileWins),
                Arguments.of(
                        "an explicit settings profile beside a default one",
                        (Build) MergeFixtures::explicitSettingsProfileBesideADefaultOne),
                Arguments.of(
                        "pom profiles named in the settings",
                        (Build) MergeFixtures::pomProfileNamedInTheSettings),
                Arguments.of(
                        "a leading dot in an expression",
                        (Build) MergeFixtures::leadingDotInAnExpression),
                Arguments.of(
                        "a project field over a property of its name",
                        (Build) MergeFixtures::projectFieldOverAPropertyOfItsName));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("merges")
    void theRepositoriesMatchWhatRealMavenAnswers(String name, Build build) throws Exception {
        Fixture fixture = new Fixture(root);
        build.on(fixture);

        Set<String> real = realMaven(fixture);
        Set<String> ours = ours(fixture.answer());

        assertThat(ours).as("%s: the same id and URL as help:effective-pom", name).isEqualTo(real);
    }

    @Test
    void aProfileOnAJdkIsTheOneNamedDifferenceFromRealMaven() throws Exception {
        Fixture fixture = new Fixture(root);
        MergeFixtures.profileOnAJdk(fixture);

        Set<String> real = realMaven(fixture);
        RepositoryHostsAnswer answer = fixture.answer();

        assertThat(real)
                .as("real Maven applies the profile, or this comparison proves nothing")
                .contains("jdk-repo https://jdk.example.org/maven");
        assertThat(ours(answer))
                .as("the machine decides, so no host is taken from the profile")
                .doesNotContain("jdk-repo https://jdk.example.org/maven");
        assertThat(answer.unknowns())
                .as("the profile is named instead")
                .anyMatch(u -> u.what().equals("profile on-jdk") && u.why().contains("JDK [1,)"));
    }

    private static Set<String> ours(RepositoryHostsAnswer answer) {
        Set<String> result = new TreeSet<>();
        answer.hosts().forEach(h -> result.add(h.id() + " " + h.url()));
        return result;
    }

    private Set<String> realMaven(Fixture fixture)
            throws IOException, InterruptedException, XmlPullParserException {
        Path output = root.resolve("effective.xml");
        Files.createDirectories(fixture.home.resolve(".m2"));
        Path settings = fixture.home.resolve(".m2/settings.xml");
        if (!Files.exists(settings)) {
            Files.writeString(settings, "<settings/>");
        }
        Path wrapper = Path.of(System.getProperty("test.maven.wrapper"));
        List<String> command = new ArrayList<>(List.of(wrapper.toString(), "-B", "-q"));
        command.addAll(localMavenFlags(wrapper));
        command.addAll(
                List.of(
                        "-s",
                        settings.toString(),
                        "-Dmaven.repo.local.tail=" + fixture.localRepository(),
                        "-Doutput=" + output,
                        "org.apache.maven.plugins:maven-help-plugin:"
                                + System.getProperty("test.maven.help.version")
                                + ":effective-pom"));
        Path log = root.resolve("maven.log");
        ProcessBuilder builder =
                new ProcessBuilder(command)
                        .directory(fixture.checkout.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile());
        builder.environment().remove("MAVEN_PROJECTBASEDIR");
        builder.environment().remove("MAVEN_ARGS");
        int exit = builder.start().waitFor();
        assertThat(exit).as("real Maven must answer: %s", Files.readString(log)).isZero();
        try (Reader reader = Files.newBufferedReader(output)) {
            Model model = new MavenXpp3Reader().read(reader);
            Set<String> result = new TreeSet<>();
            for (Repository r : model.getRepositories()) {
                result.add(r.getId() + " " + r.getUrl());
            }
            for (Repository r : model.getPluginRepositories()) {
                result.add(r.getId() + " " + r.getUrl());
            }
            return result;
        }
    }

    // A nested Maven must find the plugin where this checkout's own build keeps its downloads.
    private static List<String> localMavenFlags(Path wrapper) throws IOException {
        Path config = wrapper.resolveSibling(".mvn/maven.config");
        boolean split =
                Files.exists(config)
                        && Files.readString(config)
                                .contains("aether.enhancedLocalRepository.split=true");
        String repository = System.getProperty("test.maven.repository");
        return List.of("-Dmaven.repo.local=" + (split ? repository + "/cached" : repository));
    }
}
