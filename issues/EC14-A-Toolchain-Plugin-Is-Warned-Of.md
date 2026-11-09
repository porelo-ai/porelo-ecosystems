# EC14 — A Toolchain Plugin Is Warned Of

**Status:** Backlog.
**Size:** S - one reader beside the one for container use, wired into `detect`, and its tests; the contract does not change.

**What must be true.** A person whose pom uses a plugin that picks or fetches its own JDK reads a warning at that plugin's line in the draft, naming the JDK it asks and what a task lacks for it, instead of meeting a build that stops on a refused host or a missing toolchain.

## Why

`guava` at `592a1e6` declares both plugins in `guava/pom.xml`, configured in its root's `pluginManagement`, each asking a JDK 27:

- `org.mvnsearch:toolchains-maven-plugin` 4.5.0, goal `toolchain`, asks `<jdk><version>27</version><vendor>temurin</vendor>` and a `<testJdk>`. When no toolchain in the user's `toolchains.xml` matches, its `FoojayService` asks `api.foojay.io`, downloads the JDK into the Maven home's `jdks/` and writes the match into `toolchains.xml`. In a task the build stops on the refused `api.foojay.io`. On the reference account it fetched `jdk-27+35`, an early-access build.
- `maven-toolchains-plugin` 3.3.0, goal `toolchain`, asks `<jdk><version>27</version>` and fails the build when `toolchains.xml` holds no match. It has no skip parameter.

Read in the plugins' own `plugin.xml`: the mvnsearch goal's `skip` reads the property `skip`, though its description says `-Dtoolchain.skip`; so `-Dtoolchain.skip` does nothing, and `-Dskip=true` is read by other plugins too. A warning names neither as a way out until it has been run.

## Plan

- **`ToolchainUse`**, beside `ContainerUse` and shaped like it: `read(Model, Places)` walks `model.getBuild().getPlugins()`, and for each `toolchain` execution, or the plugin's own configuration, of the two plugins reads `toolchains/jdk/version` and `vendor` (and the mvnsearch `testJdk`) through `ToolNeeds.settings`, falling back to the build's `pluginManagement` as `PluginHosts` does. Each place yields one `Unplaced`.
- **The notes**, one per plugin and pom:
  - mvnsearch: it fetches JDK `<version>` (`<vendor>`) from `api.foojay.io` and the vendor's download host, which a draft cannot name, unless the task's `toolchains.xml` already holds one; no host is opened for it; what to write into `project.yml` yourself to let it fetch.
  - Maven's: it needs JDK `<version>` in a `toolchains.xml`, which a task does not have, so the build stops at it.
  - A version the reader cannot read (a property, or none) is named as unknown with where it stands.
- **Warn only:** no JDK a toolchain asks is fetched and no `toolchains.xml` is written; the person decides what the task gets.
- **`MavenEcosystem.detect`** concatenates `ToolchainUse`'s unplaced with the others.
- **The test that goes red first,** in `MavenEcosystemTest` beside `AContainerStartingBuild`: a pom with the mvnsearch plugin and a JDK 27 gets a warning at the plugin's line naming `27` and `api.foojay.io`; then Maven's plugin, the version from `pluginManagement`, a plugin only managed and never used (no warning), and two modules one warning each. The harm it prevents: a draft that reads as complete while the build stops at its first phase.

## Acceptance

- `guava` at `592a1e6`, through the real first start, gets both warnings at `guava/pom.xml`'s plugin lines, naming JDK 27; seen in the corpus run's `first-start.json`.
- Each test above is seen to fail with `ToolchainUse` taken out of `detect`.

## To be checked

- Whether `-Dskip=true` lets `guava` pass the mvnsearch goal in a task, and what else reads `skip` in that build; only then may a note name it.
- `maven-toolchains-plugin`'s `select-jdk-toolchain` goal, which discovers JDKs on the machine: whether it finds the JDK under `/opt/java` and what it does when none matches.
- Whether the line the warning names is the module's `<plugin>` or the managed configuration's, read with `Places` on `guava`.
