package org.fuin.porelo.ecosystem.maven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Says whether a checkout's file of Maven's options leaves the model alone. Only options known to
 * do so count: any other can set a property, name other settings or run code, and none is applied
 * here.
 */
final class OptionFiles {
    static final Pattern MAVEN_OPTIONS =
            Pattern.compile(
                    "-Daether\\.enhancedLocalRepository\\.(split|localPrefix)=[\\w./-]*"
                            + "|-B|--batch-mode|-ntp|--no-transfer-progress");
    static final Pattern JVM_OPTIONS =
            Pattern.compile(
                    "--add-(exports|opens)(=[\\w./-]+=[\\w.,-]+)?"
                            + "|[\\w.-]+/[\\w./-]+=[\\w.,-]+"
                            + "|-Xm[sx]\\d+[kKmMgG]?|-Xss\\d+[kKmMgG]?");
    private static final int MAX_BYTES = 64 * 1024;

    private OptionFiles() {}

    static boolean leaveTheModelAlone(Path file, Pattern harmless, Places places) {
        if (!places.isInTheCheckout(file)) {
            return false;
        }
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            return false;
        }
        if (bytes.length > MAX_BYTES) {
            return false;
        }
        String text = new String(bytes, StandardCharsets.UTF_8).strip();
        return text.isEmpty()
                || Arrays.stream(text.split("\\s+"))
                        .allMatch(option -> harmless.matcher(option).matches());
    }
}
