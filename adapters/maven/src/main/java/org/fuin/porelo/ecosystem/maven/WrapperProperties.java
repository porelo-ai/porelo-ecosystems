package org.fuin.porelo.ecosystem.maven;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/** What the Maven wrapper's properties say, read with the parser the wrapper itself uses. */
final class WrapperProperties {
    /** Where the wrapper keeps them, from a checkout's root. */
    static final String FILE = ".mvn/wrapper/maven-wrapper.properties";

    private static final String URL = "distributionUrl";
    private static final int MAX_BYTES = 1024 * 1024;

    private final Properties values;
    private final List<String> lines;

    private WrapperProperties(Properties values, List<String> lines) {
        this.values = values;
        this.lines = lines;
    }

    /** The checkout's wrapper properties, or none when they are missing or cannot be read. */
    static Optional<WrapperProperties> read(Path checkout) {
        Path file = checkout.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                return Optional.empty();
            }
            Properties values = new Properties();
            // The wrapper loads them from a stream, which reads ISO-8859-1.
            values.load(new ByteArrayInputStream(bytes));
            return Optional.of(
                    new WrapperProperties(
                            values,
                            new String(bytes, StandardCharsets.ISO_8859_1).lines().toList()));
        } catch (IOException | IllegalArgumentException e) {
            // The parser refuses a malformed Unicode escape, and so would the wrapper.
            return Optional.empty();
        }
    }

    /** Whether the checkout holds the file but it is too large or cannot be read. */
    static boolean unreadable(Path checkout) {
        return Files.isRegularFile(checkout.resolve(FILE)) && read(checkout).isEmpty();
    }

    /** The URL the wrapper fetches Maven from, if one is set. */
    Optional<String> url() {
        return value(URL);
    }

    /** Whether the wrapper is the script alone, which fetches with its own tools. */
    boolean onlyScript() {
        return value("distributionType").filter("only-script"::equals).isPresent();
    }

    /**
     * Whether the wrapper checks the archive's digest, which the script does only for one given.
     */
    boolean checksDigest() {
        return value("distributionSha256Sum").isPresent();
    }

    // A properties value keeps its trailing blanks, which the wrapper trims.
    private Optional<String> value(String key) {
        return Optional.ofNullable(values.getProperty(key))
                .map(String::strip)
                .filter(v -> !v.isEmpty());
    }

    /** The line, counted from 1, where the URL's entry starts, or 0 when none is found. */
    int urlLine() {
        int found = 0;
        StringBuilder entry = null;
        int start = 0;
        for (int i = 0; i < lines.size(); i++) {
            String text = withoutLeadingBlanks(lines.get(i));
            if (entry == null) {
                if (text.isEmpty() || text.startsWith("#") || text.startsWith("!")) {
                    continue;
                }
                entry = new StringBuilder();
                start = 0;
            }
            boolean goesOn = endsContinued(text);
            String part = goesOn ? text.substring(0, text.length() - 1) : text;
            if (start == 0 && !part.isEmpty()) {
                start = i + 1;
            }
            entry.append(part);
            if (!goesOn) {
                // The parser keeps the last entry of a key, so the line is the last one's.
                found = keyOf(entry).equals(URL) ? start : found;
                entry = null;
            }
        }
        return entry != null && keyOf(entry).equals(URL) ? start : found;
    }

    // The parser skips only these before a key or a continued line, not every whitespace.
    private static String withoutLeadingBlanks(String line) {
        int i = 0;
        while (i < line.length() && " \t\f".indexOf(line.charAt(i)) >= 0) {
            i++;
        }
        return line.substring(i);
    }

    // A line goes on when it ends in an odd number of backslashes.
    private static boolean endsContinued(String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            count++;
        }
        return count % 2 == 1;
    }

    // The joined entry is read by the same parser, so an escaped key names what it names.
    private static String keyOf(CharSequence entry) {
        Properties one = new Properties();
        try {
            one.load(new StringReader(entry.toString()));
        } catch (IOException | IllegalArgumentException e) {
            return "";
        }
        return one.stringPropertyNames().stream().findFirst().orElse("");
    }
}
