package org.fuin.porelo.ecosystem.maven;

import java.nio.file.Path;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.fuin.porelo.ecosystem.shared.FilePlaces;
import org.fuin.porelo.ecosystem.shared.Origin;
import org.fuin.porelo.ecosystem.shared.Place;
import org.jspecify.annotations.Nullable;

/** Says where a file stands, and where Maven's model says a value stands. */
final class Places extends FilePlaces {
    Places(Path checkout, Path home, Path... settings) {
        super(checkout, home, settings);
    }

    Place of(@Nullable InputLocation location) {
        if (location == null || location.getSource() == null) {
            return new Place("", 0, Origin.MACHINE);
        }
        InputSource source = location.getSource();
        return of(source.getLocation(), location.getLineNumber());
    }
}
