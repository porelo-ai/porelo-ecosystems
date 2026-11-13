# EC23 — The Issue Heads Are Checked Every Day

```json
{
  "$schema": "issue-1.json",
  "id": "EC23",
  "title": "The Issue Heads Are Checked Every Day",
  "state": "Backlog",
  "size": "XS",
  "priority": "Low",
  "rank": 2,
  "blockedBy": [],
  "covers": "The daily shared-rules check and each pull request's run check every issue head against issue-1.json with the released porelo-release's `issues check issues`, so a head the board refuses is caught on GitHub too."
}
```

**Why this size.** One step in one workflow, the same command the hand-over already runs by hand; no code and no
contract change.

**What must be true.** An issue head in `issues/` that `issue-1.json` refuses - no size, a rank of `null`, an index row
out of the heads' order - fails this repository's *Shared rules check*, naming the file and the field, on its daily
run and on every pull request.

## Plan

- **The step.** `.github/workflows/shared-rules.yml` gets a step after *The documents*:
  `./mvnw -B -s settings.xml -N exec:exec@release -Drelease.args="issues check issues"`, on the checked-out commit,
  with porelo-release taken from Central at the version the root pom pins, once that release carries the check's
  message for a missing `issues/workflow.yml`.
- **Level.** No test of its own: the check is porelo-release's and tested there; this is a workflow step only.

## Acceptance

- On a copy of this repository, a head without `size` and one with `"rank": null` each make the step exit 1, naming
  the file and the field; on `main` as it is, the step passes.
- `check-actions` and the *Workflow check* pass on the changed workflow.
