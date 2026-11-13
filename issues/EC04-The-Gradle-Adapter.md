# EC04 — The Gradle Adapter

```json
{
  "$schema": "issue-1.json",
  "id": "EC04",
  "title": "The Gradle Adapter",
  "state": "Analyzed",
  "size": "L",
  "priority": "High",
  "rank": 3,
  "blockedBy": ["EC24"],
  "covers": "A checkout built with Gradle gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line."
}
```

**Why this size.** a reader of Gradle's files without its model, of literals in Groovy and Kotlin scripts, its repository hosts and credentials, `Configure` as an init script, and a corpus of real builds; a design to decide first: what a script's literal may be trusted as.

**What must be true.** A checkout built with Gradle gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. Gradle has no model without running its scripts, so it reads only files: the wrapper properties, the version catalog and toolchain declarations written as literals, and names more values unknown than Maven's.

## Plan

- **A module of its own, `adapters/gradle`**, on the shared answer and packaging; its main implements `Ecosystem1`.
  A directory without `settings.gradle(.kts)` or `build.gradle(.kts)` is `NotRecognized`.
- **Tools:** Gradle from `gradle/wrapper/gradle-wrapper.properties` (`distributionUrl`, its host a host); the JDK from
  a toolchain's `languageVersion` written as a literal, then `sourceCompatibility`/`targetCompatibility`, then
  `org.gradle.java.home` in `gradle.properties`; versions in `gradle/libs.versions.toml`. A value built by code is
  unknown with its file and line. Packages from a short table of plugins known to run a program (GraalVM's native
  plugin, `com.netflix.nebula.ospackage`).
- **Rules:** repository hosts from `repositories { maven { url = ... } }` and `pluginManagement`/`dependencyResolutionManagement`
  literals in the checkout (marked from the checkout), and from the person's `init.gradle(.kts)`, `init.d/` and
  `gradle.properties` (marked yours); credentials by property name only; memory from `org.gradle.jvmargs`. `buildSrc`
  and included builds are read the same way.
- **`Configure`** writes an init script pointing each declared repository at the broker, holding no credential.
- **Level:** unit tests over fixture checkouts for each source above; the corpus as the acceptance. **Red first:** a
  `gradle.properties` with a password property answers its name only, seen red with a reader that copies values.

## Acceptance

- Judged by real builds, as `doc/decisions.md` says under *An adapter is judged by real builds*: a real build with `gradle` and its wrapper runs in a real task from the file Porelo's first start writes, with only this adapter's code fixed.
- The projects, by the traits that entry names, each checked at the clone and swapped if it lacks its trait: `caffeine` and `moshi` (small libraries), `spring-framework` (large), the Gradle build of `spring-petclinic` (application), `mockito` (convention plugins in `buildSrc`), `micrometer` (a repository of its own).
- Held back, run only at the end: `okhttp`, `kafka`, `junit-framework`.
