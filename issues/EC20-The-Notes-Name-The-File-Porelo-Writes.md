# EC20 — The Notes Name The File Porelo Writes

```json
{
  "$schema": "issue-1.json",
  "id": "EC20",
  "title": "The Notes Name The File Porelo Writes",
  "state": "Review",
  "size": "XS",
  "priority": "Medium",
  "rank": 10,
  "blockedBy": [],
  "covers": "The notes that say what to write name `porelo.yml`, the file Porelo now writes, not `project.yml`."
}
```

**Why this size.** the wording of five notes and their tests; the contract does not change.

**What must be true.** Every note of the Maven adapter that tells a person what to write names the file Porelo writes into the checkout. Porelo 0.5.1 snapshot 454 writes `porelo.yml`; the notes still say `project.yml`.

## Plan

- **The notes:** `OwnFile`, `MavenOptions`, `ContainerUse` and `ToolchainUse` say `porelo.yml`, and a test of each note that says what to write asserts that name. `doc/decisions.md` says it too.
- **Not in it:** the changelog entries already written, which say what each change did when it was made, and `AGENTS.md`'s rules, which the operator words.
