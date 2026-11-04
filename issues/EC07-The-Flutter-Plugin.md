# EC07 — The Flutter Plugin

**Status:** Accepted.

**What must be true.** A checkout built with Flutter gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. It reads `pubspec.yaml`'s `environment` and the platform directories.
