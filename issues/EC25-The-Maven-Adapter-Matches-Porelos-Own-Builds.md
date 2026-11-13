# EC25 — The Maven Adapter Matches Porelo's Own Builds

```json
{
  "$schema": "issue-1.json",
  "id": "EC25",
  "title": "The Maven Adapter Matches Porelo's Own Builds",
  "state": "Analyzed",
  "size": "S",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "Run on each of Porelo's own Maven repositories with its `porelo.yml` set aside, the Maven adapter's tools answer installs the same tools as that file, or names what it left out: `shfmt` for Spotless, `rpm` and `dpkg-dev` for the packaging plugins, GraalVM for a native build."
}
```

**Why this size.** one class's table of plugins and the programs they run, its tests, and a measurement over a dozen
checkouts on this machine; no contract change.

**What must be true.** Porelo's own repositories are the reference set: for each Maven one, the tools the adapter
answers install what its hand-written `porelo.yml` installs, at the same version, or the answer names what it left out
as unplaced. No tool is answered at another version than the file installs.

## Plan

- **The measurement first:** the adapter's JVM entry point over each Porelo checkout on `origin/main`, read only,
  against its `porelo.yml`; every difference listed in this issue, by repository.
- **`ToolNeeds`' table of plugins** gains what the reference set shows and the table lacks today: Spotless's `shfmt`
  step (`shfmt`, at the version its configuration names), the packaging plugins that call `rpmbuild` and
  `dpkg-deb` (`rpm`, `dpkg-dev`). A native build's GraalVM, already installed in its exact version, is checked here only.
- **What no adapter can know** - a repository's own pin properties such as a Node version, a binary taken from
  another image, a repository's own switches - is answered as unplaced with its file and line, never guessed.
- **Level:** unit tests over fixtures taken from these poms; the measurement is run by hand, its result written here.
  **Red first:** a fixture with Spotless's `shfmt` step answers no `shfmt` today.

## Acceptance

- For each of Porelo's Maven repositories, the list of differences between the answer and its `porelo.yml` holds only
  what is named as unplaced; none is a tool at another version.
- A pom with Spotless's `shfmt` step and one with the deb and rpm packaging each answer their packages with the
  plugin's file and line.
