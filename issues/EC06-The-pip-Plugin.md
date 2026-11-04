# EC06 — The pip Plugin

**Status:** Accepted.

**What must be true.** A checkout built with pip gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. It reads the requirements, the project file and the workflow's `setup-python`.
