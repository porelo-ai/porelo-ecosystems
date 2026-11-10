# EC17 — The Maven Adapter Says Its Version

**Status:** Review
**Size:** S - one module and its tests: the pom, one resource the native image must hold, `MavenEcosystem` and a test
of the built executable; no contract of this repository changes.

**What must be true.** When Porelo asks the Maven adapter to describe itself, the adapter answers with the version it was
built as, taken from its build and never written by hand. So a person can tell which build answered, also when a copy in
the account's own directory shadows the packaged one.

## Plan

- The contract's `Ecosystem.version()` defaults to blank, which means "says none", and `describe` then sends no
  `version`; the adapter overrides it.
- The version is the package's, `package.version`: the project's version, with a snapshot's `-SNAPSHOT` replaced by
  `~snapshot.<run>`, as apt and dnf show it. Its `build-helper-maven-plugin` execution moves from the `native` profile
  into the module's main build, so the jar and the executable carry the same value.
- A filtered resource next to `MavenEcosystem` holds `version=${package.version}`; `MavenEcosystem.version()` reads it
  on each call, which `Describe` makes once per start, and fails loudly when it is missing or still holds the unfiltered
  placeholder: the adapter is then refused, never shown with a wrong version. The resource is added to the
  module's `reachability-metadata.json`, which its test holds against the jar.
- `pom.xml` takes the contract's version with the `version()` method, built and published by Core.
- Red first: a unit test asserts that `MavenEcosystem.version()` equals the build's `package.version`, passed to it by
  Surefire, never written into it; the integration test of the native executable asserts the same of what the built
  binary answers to `describe`, through the contract's `InstalledEcosystems`. Both go red when the resource is not
  filtered. Without the metadata entry the image still holds the resource, since GraalVM registers one read by a
  constant name; the entry stays so the module's own list says what its image holds.
- The workflow's check that each unpacked package answers `describe` expects the package's own version, read from the
  `.deb`'s name, and the contract's protocol version 3.
- Not tested apart: the missing-resource and blank-version paths of `version()`. Each throws as the unfiltered
  placeholder does, which both tests were seen to catch, and a seam only for them would test the seam, not the build.

## Acceptance

- The `describe` answer of the native executable carries `version`, equal to the package version of the build that made
  it; the test fails when the resource is not filtered.
- An adapter built from another version or run number answers that version without a change to its code.
