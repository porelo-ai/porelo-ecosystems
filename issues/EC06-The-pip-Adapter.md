# EC06 — The pip Adapter

```json
{
  "$schema": "issue-1.json",
  "id": "EC06",
  "title": "The pip Adapter",
  "state": "Analyzed",
  "size": "M",
  "priority": "High",
  "rank": 5,
  "blockedBy": ["EC24"],
  "covers": "A checkout built with pip gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line."
}
```

**Why this size.** one module reading `pyproject.toml`, the requirements files, `.python-version` and pip's configuration, `Configure` as a `pip.conf`, and a corpus of real builds; no contract change.

**What must be true.** A checkout built with pip gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. It reads the requirements, the project file and the workflow's `setup-python`.

## Plan

- **A module of its own, `adapters/pip`**, on the shared answer and packaging. A directory without `pyproject.toml`,
  `setup.cfg`, `setup.py` or a `requirements*.txt` is `NotRecognized`; `setup.py` is never run, only named.
- **Tools:** Python from `.python-version`, then `requires-python`; the build backend from `[build-system]`; packages
  for sdists that compile (`python3-dev`, `gcc`) when a requirement is pinned without wheels in the person's cache.
- **Rules:** index hosts from `--index-url`/`--extra-index-url` in requirements files and `[tool.uv]`/`[tool.poetry]`
  sources (from the checkout), and from `pip.conf` at the person's and the machine's level and `PIP_INDEX_URL` (yours,
  machine); a credential in an index URL is named by its host only, and its value appears nowhere.
- **`Configure`** writes a `pip.conf` pointing each index at the broker, no credential.
- **Level:** unit tests over fixture checkouts; the corpus as the acceptance. **Red first:** an index URL with a user
  and password answers the host only, seen red with a reader that keeps the URL.

## Acceptance

- Judged by real builds, as `doc/decisions.md` says under *An adapter is judged by real builds*: a real build with `pip` runs in a real task from the file Porelo's first start writes, with only this adapter's code fixed.
- The projects, by the traits that entry names, each checked at the clone and swapped if it lacks its trait: `requests`, `click` and `flask` (small libraries), `django` (large), `httpie` (application), `numpy` (native extensions), a project with an index of its own.
- Held back, run only at the end: `attrs`, `celery`, `scrapy`.
