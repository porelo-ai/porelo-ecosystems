package org.fuin.porelo.ecosystem.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.codehaus.plexus.interpolation.InterpolationPostProcessor;
import org.jspecify.annotations.Nullable;

/**
 * Stops filling in a pom's values once they have grown beyond what is read: each level of {@code
 * <a>${b}${b}</a>} doubles, through a property or any field of the model, so a short pom can ask
 * for more than any machine holds. Measured while filling in, where no way of writing a value
 * escapes.
 */
final class BoundedInterpolator extends StringVisitorModelInterpolator {
    static final long MAX_FILLED_CHARS = 16L * 1024 * 1024;

    /** A pom asked for more than is filled in. */
    static final class FillingRefused extends RuntimeException {
        private static final long serialVersionUID = 1L;

        FillingRefused() {
            super("filled in beyond the bound", null, false, false);
        }
    }

    @Override
    protected List<? extends InterpolationPostProcessor> createPostProcessors(
            Model model, File projectDir, ModelBuildingRequest config) {
        List<InterpolationPostProcessor> processors = new ArrayList<>();
        processors.add(new Bound());
        processors.addAll(super.createPostProcessors(model, projectDir, config));
        return processors;
    }

    // One per model, so what one pom asked for does not count against the next.
    private static final class Bound implements InterpolationPostProcessor {
        private long filled;

        // The builder calls this for each use of a value, a remembered one included.
        @Override
        public @Nullable Object execute(String expression, Object value) {
            filled += String.valueOf(value).length();
            if (filled > MAX_FILLED_CHARS) {
                throw new FillingRefused();
            }
            return null;
        }
    }
}
