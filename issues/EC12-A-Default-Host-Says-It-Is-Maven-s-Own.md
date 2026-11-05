# EC12 — A Default Host Says It Is Maven's Own

**Status:** Backlog.
**Size:** XS - one note in one class; the contract does not change.

**What must be true.** A person who reads the Maven adapter's answer for a host that comes from no file, such as `repo.maven.apache.org` from Maven's built-in Central, reads that it is Maven's built-in default, not only `repository central at https://repo.maven.apache.org/maven2`.

## Plan

- The contract already allows it: a value from a tool's built-in default has `file` "", line 0 and origin `MACHINE`, and this repository's answer does that. So `file` and `line` stay, and the words go into `note`.
- `MavenEcosystem.noteOf` starts the note with "Maven's built-in" when `host.place().file()` is empty, for a repository and a plugin repository alike.
- Only the wording changes, so it gets no test of its own. A test that holds this note verbatim changes with it, if one does.

## Acceptance

- On a checkout with no repository of its own, `porelo project init --json` answers `host.repo.maven.apache.org` with a note that says it is Maven's built-in default, with `file` "" and line 0 as before.
- A host from a file keeps its note as it is.
