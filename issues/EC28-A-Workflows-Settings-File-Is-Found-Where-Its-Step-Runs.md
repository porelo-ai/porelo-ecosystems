# EC28 — A Workflow's Settings File Is Found Where Its Step Runs

```json
{
  "$schema": "issue-1.json",
  "id": "EC28",
  "title": "A Workflow's Settings File Is Found Where Its Step Runs",
  "state": "Implementing",
  "size": "S",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "What the reading of the shell form left over: a step's working directory, a folded block, a heredoc, a block of another key, a substitution too deep, and the notes on links and the limit of files."
}
```

**Why this size.** The same two classes of the Maven adapter and their tests; no contract change.

**What must be true.** The settings file the adapter answers for a workflow is the file Maven reads when the step
runs, and a line the adapter does not read as a command is one the shell does not run either.

## Plan

- **Where the step runs.** A step's `working-directory:` and a `cd sub &&` before Maven resolve the file against that
  directory, not the checkout's root; today `cd sub && mvn -s settings.xml` answers the root's `settings.xml`.
- **YAML blocks.** A folded `>`/`>-` block joins its lines without a `\`; the lines of a block under another key
  (`description: |`) and the body of a heredoc are not read as commands.
- **Depth.** A `sh -c` or substitution nested past the limit gives a note naming the workflow instead of passing
  silently.
- **Notes.** A directory named like a workflow (`x.yml/`) is not called "outside the checkout, by a link"; a workflow
  linked from outside counts against the 64 read; the notes come in the order of the files.
- **Level:** unit tests in `MavenEcosystemTest.OwnSettings`. **Red first:** `cd sub && mvn -s settings.xml` with
  `sub/settings.xml` and a root `settings.xml` answers the root's file today.

## Acceptance

- Each case above has a test that failed before and passes after.
