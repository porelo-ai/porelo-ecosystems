package org.fuin.porelo.ecosystem.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationFile;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.activation.ProfileActivator;
import org.jspecify.annotations.Nullable;

/**
 * A profile's activation that only where the build runs can decide: a JDK, an operating system, the
 * environment or a file outside the checkout. Such a profile stays off and is named; it comes
 * first, since the builder otherwise goes by the conditions it can evaluate.
 */
final class MachineCondition implements ProfileActivator {
    private static final Set<String> OF_THE_MACHINE =
            Set.of("env.", "java.", "os.", "user.", "file.", "line.", "path.");

    private final Path checkout;

    MachineCondition(Path checkout) {
        this.checkout = checkout;
    }

    @Override
    public boolean isActive(
            Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        return false;
    }

    @Override
    public boolean presentInConfig(
            Profile profile, ProfileActivationContext context, ModelProblemCollector problems) {
        return of(profile.getActivation()) != null;
    }

    /** What the activation depends on, for a person to read; null when it can be evaluated. */
    @Nullable String of(@Nullable Activation activation) {
        if (activation == null) {
            return null;
        }
        List<String> conditions = new ArrayList<>();
        if (activation.getJdk() != null) {
            conditions.add("JDK " + activation.getJdk());
        }
        if (activation.getOs() != null) {
            conditions.add("the operating system");
        }
        ActivationProperty property = activation.getProperty();
        if (property != null && isOfTheMachine(String.valueOf(property.getName()))) {
            conditions.add("the property " + property.getName());
        }
        ActivationFile file = activation.getFile();
        if (file != null
                && !(isInTheCheckout(file.getExists()) && isInTheCheckout(file.getMissing()))) {
            conditions.add("a file outside the checkout");
        }
        return conditions.isEmpty() ? null : String.join(", ", conditions);
    }

    private static boolean isOfTheMachine(String name) {
        String plain = name.startsWith("!") ? name.substring(1) : name;
        return OF_THE_MACHINE.stream().anyMatch(plain::startsWith);
    }

    private boolean isInTheCheckout(@Nullable String file) {
        if (file == null) {
            return true;
        }
        String path =
                file.replace("${basedir}", checkout.toString())
                        .replace("${project.basedir}", checkout.toString());
        return !path.contains("${") && checkout.resolve(path).normalize().startsWith(checkout);
    }
}
