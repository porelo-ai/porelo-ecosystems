package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.jspecify.annotations.Nullable;

/** Says whose a file is, and which files may be read at all. */
final class Places {
    private final Path checkout;
    private final Path home;
    private final Path settings;
    private final List<Path> localRepositories;

    Places(Path checkout, Path home, Path settings, List<Path> localRepositories) {
        this.checkout = realOrNormal(checkout);
        this.home = realOrNormal(home);
        this.settings = realOrNormal(settings);
        this.localRepositories = localRepositories.stream().map(Places::realOrNormal).toList();
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
        // First: settings standing in the checkout are written by whoever can push.
        if (real.startsWith(checkout)) {
            String inCheckout = SafeText.of(checkout.relativize(real).toString());
            return new Place(inCheckout, line, Origin.CHECKOUT);
        }
        Origin origin = real.equals(settings) ? Origin.YOURS : Origin.MACHINE;
        return new Place(SafeText.of(relativeToHome(real)), line, origin);
    }

    /** Whether a file lies in the checkout or a local repository, its links followed. */
    boolean mayRead(Path file) {
        Path real;
        try {
            real = file.toRealPath();
        } catch (IOException e) {
            return false;
        }
        if (!Files.isRegularFile(real)) {
            return false;
        }
        return real.startsWith(checkout) || localRepositories.stream().anyMatch(real::startsWith);
    }

    /** Where a pom named in the checkout stands by its name, wherever a link would take it. */
    Place asNamedInTheCheckout(Path pom) {
        Path named = pom.toAbsolutePath().normalize();
        String file = named.startsWith(checkout) ? checkout.relativize(named).toString() : "pom";
        return new Place(SafeText.of(file), 0, Origin.CHECKOUT);
    }

    /** A message of the builder's, which names files by the machine's paths. */
    String withoutThePaths(String message) {
        return message.replace(checkout.toString(), "<checkout>")
                .replace(home.toString(), "<home>");
    }

    /** Whether a pom may be walked as part of the checkout, which a parent's place need not. */
    boolean isInTheCheckout(Path file) {
        return mayRead(file) && realOrNormal(file).startsWith(checkout);
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
