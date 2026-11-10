package org.fuin.porelo.ecosystem.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Harm prevented: the place named for the wrapper's Maven is not where the build reads it. */
class WrapperPropertiesTest {
    private static final String URL = "distributionUrl=https\\://dl.example.org/m-3.9.15-bin.zip";
    private static final String OTHER = "distributionUrl=https\\://other.example.org/m-bin.zip";

    @TempDir Path checkout;

    static Stream<Arguments> files() {
        return Stream.of(
                Arguments.of("after another entry", "a=b\n" + URL + "\n", 2),
                Arguments.of("written twice, the last counts", OTHER + "\n" + URL + "\n", 2),
                Arguments.of(
                        "a continued value that looks like the key",
                        "a=x\\\n" + OTHER + "\n" + URL + "\n",
                        3),
                Arguments.of(
                        "its key continued",
                        "distribution\\\nUrl=https\\://dl.example.org/m-3.9.15-bin.zip\n",
                        1),
                Arguments.of(
                        "continued right after its key",
                        "distributionUrl\\\n=https\\://dl.example.org/m-3.9.15-bin.zip\n",
                        1),
                Arguments.of("after a line of a backslash alone", "  \\\n" + URL + "\n", 2),
                Arguments.of(
                        "its key escaped",
                        "distribution\\u0055rl=https\\://dl.example.org/m-3.9.15-bin.zip\n",
                        1),
                Arguments.of(
                        "an escape continued over two lines",
                        "distributionUrl=https\\://dl.example.org/m-3.9.15-bin.\\u007\\\n  aip\n",
                        1),
                Arguments.of(
                        "a vertical tab, no comment, swallows the next line",
                        URL + "\n\u000b#\\\n" + OTHER + "\n",
                        1),
                Arguments.of("after a comment ending in a backslash", "# c\\\n" + URL + "\n", 2),
                Arguments.of("with CRLF", "a=b\r\n" + URL + "\r\n", 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("files")
    void theLineIsWhereTheEntryTheParserKeepsStarts(String what, String file, int line)
            throws IOException {
        write(file);

        WrapperProperties read = WrapperProperties.read(checkout).orElseThrow();

        assertThat(read.url())
                .as("the value the parser keeps")
                .hasValueSatisfying(
                        u -> assertThat(u).startsWith("https://dl.example.org/m-3.9.15-bin."));
        assertThat(read.urlLine()).as(what).isEqualTo(line);
    }

    @Test
    void aTrailingSpaceAfterTheScriptTypeStillMeansTheScript() throws IOException {
        write("distributionType=only-script \t\n" + URL + "\n");

        assertThat(WrapperProperties.read(checkout).orElseThrow().onlyScript())
                .as("a properties value keeps its trailing blanks")
                .isTrue();
    }

    @Test
    void anEmptyDigestChecksNothing() throws IOException {
        write(URL + "\ndistributionSha256Sum=  \n");

        assertThat(WrapperProperties.read(checkout).orElseThrow().checksDigest())
                .as("the script checks only a digest that is not empty")
                .isFalse();
    }

    @Test
    void aFileTooLargeIsNotRead() throws IOException {
        write("a=b\n".repeat(300_000) + URL + "\n");

        assertThat(WrapperProperties.read(checkout))
                .as("a checkout's file is bounded like every other it holds")
                .isEmpty();
        assertThat(WrapperProperties.unreadable(checkout)).as("and that is said").isTrue();
    }

    private void write(String text) throws IOException {
        Path file = checkout.resolve(WrapperProperties.FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.ISO_8859_1);
    }
}
