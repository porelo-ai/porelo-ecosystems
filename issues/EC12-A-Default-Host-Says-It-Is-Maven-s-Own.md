# EC12 — A Default Host Says It Is Maven's Own

```json
{
  "$schema": "issue-1.json",
  "id": "EC12",
  "title": "A Default Host Says It Is Maven's Own",
  "state": "Review",
  "size": "XS",
  "priority": "Medium",
  "rank": 7,
  "blockedBy": [],
  "covers": "A host that comes from no file, such as Maven's built-in Central, says in its note that it is Maven's built-in default."
}
```

**Why this size.** one note in one class; the contract does not change.

**What must be true.** A person who reads the Maven adapter's answer for a host that comes from no file, such as `repo.maven.apache.org` from Maven's built-in Central, reads that it is Maven's built-in default, not only `repository central at https://repo.maven.apache.org/maven2`.

## Plan

- The contract already allows it: a value from a tool's built-in default has `file` "", line 0 and origin `MACHINE`, and this repository's answer does that. So `file` and `line` stay, and the words go into `note`.
- `MavenEcosystem.noteOf` starts the note with "Maven's built-in" when `host.place().file()` is empty and the URL is Maven's own Central, for a repository and a plugin repository alike.
- The wording itself gets no test. A checkout can move the super POM's Central by the property `maven.repo.central`, and that host has no file either; calling it Maven's built-in would let a host the checkout chose pass for Central, so that case has a test, seen red first.

## Acceptance

- On a checkout with no repository of its own, `porelo project init --json` answers `host.repo.maven.apache.org` with a note that says it is Maven's built-in default, with `file` "" and line 0 as before.
- A host from a file keeps its note as it is.

## Reading

- Read by another model against `java-code-review` and `clean-code`: one finding, taken - a Central moved by `maven.repo.central` was called Maven's built-in. Its other side, that such a host names no file and line, stays as [EC19](README.md#ec19).
