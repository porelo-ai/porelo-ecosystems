package org.fuin.porelo.ecosystem.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.fuin.porelo.ecosystem.api.Finding;
import org.junit.jupiter.api.Test;

/** Harm prevented: Porelo's contract refuses an adapter's whole answer over one value's place. */
class AnswersTest {
    @Test
    void aValueWithNoFileIsTheMachinesAtLineZeroWhoeverWasSaidToWriteIt() {
        Finding finding = Answers.rule("host.a", "a", new Place("", 7, Origin.CHECKOUT), "n");

        assertThat(finding.origin())
                .as("only the machine may have no file")
                .isEqualTo(Finding.Origin.MACHINE);
        assertThat(finding.line()).as("a line in no file is no line").isZero();
    }

    @Test
    void aValueInAFileKeepsItsFileLineAndOrigin() {
        Finding finding =
                Answers.tool("java.version", "21", new Place("pom.xml", 7, Origin.YOURS), "n");

        assertThat(finding.file()).as("the file").isEqualTo("pom.xml");
        assertThat(finding.line()).as("the line").isEqualTo(7);
        assertThat(finding.origin()).as("who wrote it").isEqualTo(Finding.Origin.YOURS);
    }
}
