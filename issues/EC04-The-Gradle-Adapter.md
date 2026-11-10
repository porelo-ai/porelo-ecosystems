# EC04 — The Gradle Adapter

```json
{
  "$schema": "issue-1.json",
  "id": "EC04",
  "title": "The Gradle Adapter",
  "state": "Accepted",
  "size": "-",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "A checkout built with Gradle gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line."
}
```

**What must be true.** A checkout built with Gradle gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. Gradle has no model without running its scripts, so it reads only files: the wrapper properties, the version catalog and toolchain declarations written as literals, and names more values unknown than Maven's.

## Acceptance

- Judged by real builds, as `doc/decisions.md` says under *An adapter is judged by real builds*: a real build with `gradle` and its wrapper runs in a real task from the file Porelo's first start writes, with only this adapter's code fixed.
- The projects, by the traits that entry names, each checked at the clone and swapped if it lacks its trait: `caffeine` and `moshi` (small libraries), `spring-framework` (large), the Gradle build of `spring-petclinic` (application), `mockito` (convention plugins in `buildSrc`), `micrometer` (a repository of its own).
- Held back, run only at the end: `okhttp`, `kafka`, `junit-framework`.
