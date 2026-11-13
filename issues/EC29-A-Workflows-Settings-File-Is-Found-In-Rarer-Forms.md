# EC29 — A Workflow's Settings File Is Found In Rarer Forms

```json
{
  "$schema": "issue-1.json",
  "id": "EC29",
  "title": "A Workflow's Settings File Is Found In Rarer Forms",
  "state": "Backlog",
  "size": "S",
  "priority": "Low",
  "rank": 3,
  "blockedBy": [],
  "covers": "What the reading of the step's directory left over: forms where the adapter now takes no file though the build names one, and a line number inside a folded block."
}
```

**Why this size.** The same two classes of the Maven adapter and their tests; no contract change. Each case below
is a miss, never a wrong file: the generated settings file stands in.

**What must be true.** Where the step's shell and its directory can be known, the settings file it passes to Maven is
found, at the line that names it.

## Plan

- **Maven's options.** `-ssettings.xml` and `-Bs settings.xml`, the short option joined with its value or others.
- **The step's shell.** A step with `shell: pwsh` or `cmd` is read with that shell's quoting, so a path with `\` is
  found; `python` and other shells are left out.
- **YAML.** A merge key (`<<: *defaults`) in a job or step is followed.
- **Directories.** A `cd` after `&&` that surely runs (`set -e; true && cd sub`), a subshell over several lines, and
  `actions/checkout` with `path:` moving the root are read rather than taken as unknown.
- **Lines.** A file named in a folded or multi-line quoted value is placed at its own line, not the value's first.
- **Level:** unit tests in `MavenEcosystemTest.OwnSettings`. **Red first:** `mvn -ssettings.xml verify` answers no
  `ownSettings` today.

## Acceptance

- Each case above has a test that failed before and passes after.
