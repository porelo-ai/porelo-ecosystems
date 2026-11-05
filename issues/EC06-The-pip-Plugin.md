# EC06 — The pip Plugin

**Status:** Accepted.

**What must be true.** A checkout built with pip gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. It reads the requirements, the project file and the workflow's `setup-python`.

## Acceptance

- Judged by real builds, as `doc/decisions.md` says under *An adapter is judged by real builds*: a real build with `pip` runs in a real task from the file Porelo's first start writes, with only this adapter's code fixed.
- The projects, by the traits that entry names, each checked at the clone and swapped if it lacks its trait: `requests`, `click` and `flask` (small libraries), `django` (large), `httpie` (application), `numpy` (native extensions), a project with an index of its own.
- Held back, run only at the end: `attrs`, `celery`, `scrapy`.
