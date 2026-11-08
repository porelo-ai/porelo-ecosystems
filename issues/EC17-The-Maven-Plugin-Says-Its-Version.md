# EC17 — The Maven Plugin Says Its Version

**Status:** Backlog
**Size:** S - one module and its tests: the pom, one resource the native image must hold, `MavenEcosystem` and a test
of the built executable; no contract of this repository changes.

**What must be true.** When Porelo asks the Maven plugin to describe itself, the plugin answers with the version it was
built as, taken from its build and never written by hand. So a person can tell which build answered, also when a copy in
the account's own directory shadows the packaged one.

## Plan

- The contract's `Ecosystem.version()` defaults to blank, which means "says none", and `describe` then sends no
  `version`; the plugin overrides it.
- The version is the package's, `package.version`: the project's version, with a snapshot's `-SNAPSHOT` replaced by
  `~snapshot.<run>`, as apt and dnf show it. Its `build-helper-maven-plugin` execution moves from the `native` profile
  into the module's main build, so the jar and the executable carry the same value.
- A filtered resource next to `MavenEcosystem` holds `version=${package.version}`; `MavenEcosystem.version()` reads it
  once and fails loudly when it is missing or still holds the unfiltered placeholder. The resource is added to the
  module's `reachability-metadata.json`, which its test holds against the jar.
- `pom.xml` takes the contract's version with the `version()` method, built and published by Core.
- Red first: a test runs `describe` through `EcosystemMain` on `MavenEcosystem` and asserts that `version` equals the
  build's `package.version`, passed to the test by Surefire, never written into it. The integration test of the native
  executable asserts the same of the built binary.

## Acceptance

- The `describe` answer of the native executable carries `version`, equal to the package version of the build that made
  it; the test fails when the resource is not filtered or not in the image.
- A plugin built from another version or run number answers that version without a change to its code.

## To be checked

- That the contract with `Ecosystem.version()` is on porelo's main and published, and under which version of
  `porelo-ecosystem-api`.
