# EC26 — A Checked-In Settings File Is The Task's Alone

```json
{
  "$schema": "issue-1.json",
  "id": "EC26",
  "title": "A Checked-In Settings File Is The Task's Alone",
  "state": "Review",
  "size": "S",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "When the checkout holds the Maven settings its build uses, `Configure` writes no `.m2/settings.xml` for the task, and the draft names the checked-in file and says nothing was generated; only without one does the generated file stand in."
}
```

**Why this size.** one finding more in `Detect`, one key more in the `extensions.maven` block `Configure` reads, the
notes and their tests in one module; the contract does not change.

**What must be true.** When the checkout holds its own Maven settings - a `settings.xml` its build names with `-s` or
`--settings` in `.mvn/maven.config`, or one inside the checkout that the checkout's own build commands name with `-s` -
`Configure` answers no `.m2/settings.xml`, so the checked-in file alone counts in the task. The draft names that file,
with the file and line that name it, and says that no settings file is generated. Only a checkout without such a file
gets the generated one.

## Plan

- **`Detect`:** `MavenOptions` already follows `-s` in `.mvn/maven.config` and places the file; when it lies inside the
  checkout, `MavenEcosystem` answers `extension.ownSettings` with its path relative to the checkout, at the line of
  `maven.config` that names it, and a note: "the build uses the checked-in settings; Porelo generates none". The same
  for a `settings.xml` inside the checkout that a CI workflow of the checkout (`.github/workflows/*.yml`) passes with
  `-s` to `mvn` or `mvnw`. A `-s` naming a file outside the checkout is the person's, and changes nothing here.
- **`Configure`:** `MavenExtension` reads `ownSettings` beside `mirrorOf`: a path relative to the task's checkout,
  without `..` and not absolute, else refused naming the key. With it set, `Configure` answers no file.
- **Porelo passes nothing new:** the draft turns `extension.*` findings into the `extensions.maven` block, and the
  start passes that block to `Configure`, as it does `mirrorOf` today. The draft's block must reach the start by the
  same path as its `tool_repositories`.
- **Level:** unit tests in `MavenEcosystemTest` (`Rules`, `Configure`). **Red first:** a checkout with
  `.mvn/maven.config` holding `-s settings.xml` and a settings file naming a repository: `Configure` with the
  draft's block answers a `.m2/settings.xml` today, and must answer none.

## Acceptance

- `-s settings.xml` in `.mvn/maven.config`, and a workflow running `./mvnw -s settings.xml`, each answer
  `extension.ownSettings = settings.xml` with that file and line, and `Configure` with that block answers no file.
- A checkout without one, or with `-s` naming a file outside it, still gets the generated `.m2/settings.xml`.
- `ownSettings` holding `../x` or an absolute path is refused, naming `extensions.maven.ownSettings`.

## As built

- **`OwnSettings`** answers `extension.ownSettings` for `-s`/`--settings` in `.mvn/maven.config` (at the line of its
  value) or a workflow's `mvn`/`mvnw -s`, quoted or not; a comment, a path outside the checkout and a link that leads
  out of it count for nothing. `MavenExtension.ownSettings` reads the key, refusing a path outside the checkout by its
  name, and `Configure` then answers no file after `mirrorOf` is checked as before.
- **The reading** (another model) found one red - a commented line naming a settings file was taken as the build's -
  fixed with a test seen red first, as were a link leading out of the checkout and a quoted path. What else it found
  is [EC27](README.md#ec27).
- **The module's tests run with a heap of 1 GiB**, set in its pom: unbounded, the fork took a quarter of the
  machine's memory.

## To be checked

- Which other places name a checked-in settings file often enough to read (a `Makefile`, a GitLab CI file); judged
  on the corpus.
