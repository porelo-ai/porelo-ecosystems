# EC24 — The Adapters Share Their Answer And Packaging

```json
{
  "$schema": "issue-1.json",
  "id": "EC24",
  "title": "The Adapters Share Their Answer And Packaging",
  "state": "Analyzed",
  "size": "M",
  "priority": "High",
  "rank": 2,
  "blockedBy": [],
  "covers": "What every adapter needs and the Maven adapter holds alone today - the answer with file, line and origin, the unknown value, the exact-version download, the native build and the package - moves to `adapters/shared` and the parent pom, and the Build publishes a package per adapter, so a second adapter is only its own reading."
}
```

**Why this size.** several modules and the Build's publishing, no contract change: classes move, the native and package
configuration becomes the parent's, and the workflow learns a list of adapters instead of one.

**What must be true.** A second adapter answers over the contract, builds as a native executable and ships in its own
package by naming its main class and its package name, with no copy of the Maven adapter's code, and the Build
publishes and checks each adapter's package as it does the Maven one's today.

## Plan

- **What moves to `adapters/shared`** (package `org.fuin.porelo.ecosystem.shared`), read in `adapters/maven` today:
  `Answers`, `Place`, `Places`, `Origin`, `UnknownValue`, `Unplaced`, `OwnFile` (the notes naming `porelo.yml`),
  `ToolDownload`, `ToolDownloads`, `ToolLines`, `ToolUnavailable`, `VendorHttp`. What reads a pom or Maven's settings
  stays in `maven`.
- **The parent `adapters/pom.xml`** takes the `native` profile, the packaging of a deb and an rpm installing
  `/usr/libexec/porelo/ecosystems/<tool>`, and the third-party notices, each adapter setting only its main class and
  name.
- **The Build** (`.github/workflows/build.yml`) loops over the adapters that hold code, for the package checks, the
  publishing and the index probe, instead of naming `adapters/maven`.
- **Level:** unit tests move with their classes; the native executable's test (`NativeExecutableIT`) stays one per
  adapter. **Red first:** a test in `shared` that an answer naming a credential carries its id and no value, moved
  from `maven` and seen red against a copy that passes the value through.

## Acceptance

- `adapters/maven` builds, answers and packages byte for byte the same keys and notes as before, on its existing
  tests and the native executable's test.
- A stub adapter of a few lines in a test builds into a native executable and a package with the shared code alone.

## To be checked

- Whether the third-party notices' generator can run per adapter from the parent, or each adapter names it.
