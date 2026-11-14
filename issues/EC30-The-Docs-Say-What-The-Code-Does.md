# EC30 — The Docs Say What The Code Does

```json
{
  "$schema": "issue-1.json",
  "id": "EC30",
  "title": "The Docs Say What The Code Does",
  "state": "Review",
  "size": "S",
  "priority": "Highest",
  "rank": 1,
  "blockedBy": [],
  "covers": "Every sentence of the user-facing documents - the READMEs, doc/ and the unreleased changelog entries - is borne out by the code on main; a sentence the code contradicts is fixed, and code that is wrong is filed as a bug of its own."
}
```

**Why this size.** A few short pages and the unreleased changelog entries, read against the Maven adapter; documents
only, no behavior change.

**What must be true.** A person who reads this repository's documents finds every command, flag, path, default, state,
message and behavior they name in the code as it is.

## Plan

- A read-only sub-agent reads `README.md`, every module's `README.md`, `doc/` and `changelog/unreleased/` page by page
  against the code on `main`, and lists each sentence the code does not bear out, with the claim's file and line and
  the code's file and line that contradicts it. It runs no test suite and no VM.
- Each finding is checked in the code before anything changes; only a confirmed one is acted on.
- Where the code is right, the document is fixed; where the document is right and the code wrong, a bug is filed as
  its own issue instead. No code changes here.

## Acceptance

- The count of claims checked, confirmed, fixed and filed as bugs is stated at hand-over.
- The documents tests, `check-commits`, `check-citations` and `issues check issues` pass.
