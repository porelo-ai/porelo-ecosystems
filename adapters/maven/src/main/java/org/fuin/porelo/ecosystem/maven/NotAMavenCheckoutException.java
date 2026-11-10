package org.fuin.porelo.ecosystem.maven;

import java.io.Serial;

/** The directory given holds no {@code pom.xml} to read. */
public final class NotAMavenCheckoutException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    NotAMavenCheckoutException(String directory) {
        super("no pom.xml in " + directory);
    }
}
