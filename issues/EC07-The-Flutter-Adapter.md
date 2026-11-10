# EC07 — The Flutter Adapter

```json
{
  "$schema": "issue-1.json",
  "id": "EC07",
  "title": "The Flutter Adapter",
  "state": "Accepted",
  "size": "-",
  "priority": "Medium",
  "rank": 5,
  "blockedBy": [],
  "covers": "A checkout built with Flutter gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line."
}
```

**What must be true.** A checkout built with Flutter gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. It reads `pubspec.yaml`'s `environment` and the platform directories.

## Acceptance

- Judged by real builds, as `doc/decisions.md` says under *An adapter is judged by real builds*: a real build with `flutter` runs in a real task from the file Porelo's first start writes, with only this adapter's code fixed.
- The projects, by the traits that entry names, each checked at the clone and swapped if it lacks its trait: `bloc` and `getx` (libraries), `flutter/packages` (large, many packages), `flutter/samples` and `localsend` (applications), one with a native plugin, one with a package host of its own.
- Held back, run only at the end: `riverpod`, `flame`, `immich`'s mobile app.
