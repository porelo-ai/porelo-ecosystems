# EC20 — The Notes Name The File Porelo Writes

**Status:** Implementing.
**Size:** XS - the wording of five notes and their tests; the contract does not change.

**What must be true.** Every note of the Maven adapter that tells a person what to write names the file Porelo writes into the checkout. Porelo 0.5.1 snapshot 454 writes `porelo.yml`; the notes still say `project.yml`.

## Plan

- **The notes:** `OwnFile`, `MavenOptions`, `ContainerUse` and `ToolchainUse` say `porelo.yml`, and their tests assert that name and that `project.yml` is gone.
- **Not in it:** the changelog entries already written, which say what each change did when it was made, and `AGENTS.md`'s rules, which the operator words.
