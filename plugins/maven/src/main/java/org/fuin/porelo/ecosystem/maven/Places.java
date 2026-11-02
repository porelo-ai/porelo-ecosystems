package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.jspecify.annotations.Nullable;

/** Says where a file stands, and how a value's place is written. */
final class Places {
    private final Path checkout;
    private final Path home;
    private final Set<Path> yours;

    Places(Path checkout, Path home, Path... settings) {
        this.checkout = realOrNormal(checkout);
        this.home = realOrNormal(home);
        this.yours = Stream.of(settings).map(Places::realOrNormal).collect(Collectors.toSet());
    }

    Path checkout() {
        return checkout;
    }

    Place of(@Nullable InputLocation location) {
        if (location == null || location.getSource() == null) {
            return new Place("", 0, Origin.MACHINE);
        }
        InputSource source = location.getSource();
        return of(source.getLocation(), location.getLineNumber());
    }

    Place of(@Nullable String location, int line) {
        Path file = fileOf(location);
        if (file == null) {
            return new Place("", 0, Origin.MACHINE);
        }
        return of(file, line);
    }

    Place of(Path file, int line) {
        Path real = realOrNormal(file);
        // First: settings standing in the checkout are the checkout's, wherever it lies.
        if (real.startsWith(checkout)) {
            String inCheckout = checkout.relativize(real).toString();
            return new Place(inCheckout, line, Origin.CHECKOUT);
        }
        Origin origin =
                yours.contains(real) && real.startsWith(home) ? Origin.YOURS : Origin.MACHINE;
        return new Place(relativeToHome(real), line, origin);
    }

    /** A message of the builder's, which names files by the machine's paths. */
    String withoutThePaths(String message) {
        return message.replace(checkout.toString(), "<checkout>")
                .replace(home.toString(), "<home>");
    }

    private String relativeToHome(Path real) {
        return real.startsWith(home) ? home.relativize(real).toString() : real.toString();
    }

    // Every pom's source is an absolute path; the built-in pom's is a jar's URL.
    private static @Nullable Path fileOf(@Nullable String location) {
        if (location == null) {
            return null;
        }
        try {
            Path file = Path.of(location);
            return file.isAbsolute() ? file : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    static Path realOrNormal(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
