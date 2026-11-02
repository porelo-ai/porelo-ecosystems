# EC04 — The Gradle Plugin

**Status:** Accepted; blocked by the contract in Porelo.

**What must be true.** A checkout built with Gradle gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network, every value with its file and line. Gradle has no model without running its scripts, so it reads only files: the wrapper properties, the version catalog and toolchain declarations written as literals, and names more values unknown than Maven's.
