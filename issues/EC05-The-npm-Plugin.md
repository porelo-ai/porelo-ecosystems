# EC05 — The npm Plugin

**Status:** Accepted; blocked by the contract in Porelo.

**What must be true.** A checkout built with npm gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network, every value with its file and line. It reads `package.json`, the lock file and the workflow's `setup-node`.
