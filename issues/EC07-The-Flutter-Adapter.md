# EC07 — The Flutter Adapter

```json
{
  "$schema": "issue-1.json",
  "id": "EC07",
  "title": "The Flutter Adapter",
  "state": "Analyzed",
  "size": "M",
  "priority": "High",
  "rank": 6,
  "blockedBy": ["EC24"],
  "covers": "A checkout built with Flutter gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line."
}
```

**Why this size.** one module reading `pubspec.yaml`, the platform directories and pub's hosted URLs, the Flutter SDK in its exact version, and a corpus of real builds; no contract change.

**What must be true.** A checkout built with Flutter gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. It reads `pubspec.yaml`'s `environment` and the platform directories.

## Plan

- **A module of its own, `adapters/flutter`**, on the shared answer and packaging. A directory without `pubspec.yaml`
  is `NotRecognized`; a Dart package without a `flutter` dependency is answered as Dart.
- **Tools:** Flutter from `environment.flutter`, Dart from `environment.sdk`, installed in its exact version by the shared download,
  as the JDK is; the platform directories give packages: `linux/` the GTK and `clang`, `cmake`, `ninja-build`,
  `pkg-config` set Porelo's own Flutter build installs.
- **Rules:** hosts from `hosted:` URLs in `pubspec.yaml` and `pubspec.lock` (from the checkout) and `PUB_HOSTED_URL`
  (machine); pub's token store is named by host only.
- **`Configure`** sets `PUB_HOSTED_URL` to the broker when a host of the person's own is named.
- **Level:** unit tests over fixture checkouts; the corpus as the acceptance. **Red first:** a `pubspec.yaml` with a
  `linux/` directory answers its packages, seen red before the table exists.

## Acceptance

- Judged by real builds, as `doc/decisions.md` says under *An adapter is judged by real builds*: a real build with `flutter` runs in a real task from the file Porelo's first start writes, with only this adapter's code fixed.
- The projects, by the traits that entry names, each checked at the clone and swapped if it lacks its trait: `bloc` and `getx` (libraries), `flutter/packages` (large, many packages), `flutter/samples` and `localsend` (applications), one with a native plugin, one with a package host of its own.
- Held back, run only at the end: `riverpod`, `flame`, `immich`'s mobile app.
