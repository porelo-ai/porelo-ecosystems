package org.fuin.porelo.ecosystem.shared;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Says where a file stands, and how a value's place is written. */
public class FilePlaces {
    private final Path checkout;
    private final Path home;
    private final Set<Path> yours;

    /**
     * Places files against a checkout and a home.
     *
     * @param checkout the checkout read
     * @param home the person's home
     * @param settings the person's own files, which are theirs where they lie under the home
     */
    public FilePlaces(Path checkout, Path home, Path... settings) {
        this.checkout = realOrNormal(checkout);
        this.home = realOrNormal(home);
        this.yours = Stream.of(settings).map(FilePlaces::realOrNormal).collect(Collectors.toSet());
    }

    /**
     * The checkout, as its real path.
     *
     * @return the checkout
     */
    public Path checkout() {
        return checkout;
    }

    /**
     * The place of a line in a file named by a model's source.
     *
     * @param location the source's location, an absolute path or anything else for no file
     * @param line the line
     * @return its place, no file and {@link Origin#MACHINE} when it names none
     */
    public Place of(@Nullable String location, int line) {
        Path file = fileOf(location);
        if (file == null) {
            return new Place("", 0, Origin.MACHINE);
        }
        return of(file, line);
    }

    /**
     * The place of a line in a file.
     *
     * @param file the file
     * @param line the line
     * @return its place: in the checkout, the person's own, or the machine's
     */
    public Place of(Path file, int line) {
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

    /**
     * A message of a reader's, which names files by the machine's paths.
     *
     * @param message the message
     * @return it with the checkout and the home named instead of their paths
     */
    public String withoutThePaths(String message) {
        return message.replace(checkout.toString(), "<checkout>")
                .replace(home.toString(), "<home>");
    }

    private String relativeToHome(Path real) {
        return real.startsWith(home) ? home.relativize(real).toString() : real.toString();
    }

    // A file read is named by its absolute path; a tool's built-in one by a jar's URL or nothing.
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

    /**
     * A path as it really lies, or normalized when it does not exist.
     *
     * @param path the path
     * @return its real or normal form
     */
    public static Path realOrNormal(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}
