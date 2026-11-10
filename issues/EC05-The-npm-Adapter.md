# EC05 — The npm Adapter

```json
{
  "$schema": "issue-1.json",
  "id": "EC05",
  "title": "The npm Adapter",
  "state": "Accepted",
  "size": "-",
  "priority": "Medium",
  "rank": 3,
  "blockedBy": [],
  "covers": "A checkout built with npm gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line."
}
```

**What must be true.** A checkout built with npm gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. It reads `package.json`, the lock file and the workflow's `setup-node`.

## Acceptance

- Judged by real builds, as `doc/decisions.md` says under *An adapter is judged by real builds*: a real build with `npm` runs in a real task from the file Porelo's first start writes, with only this adapter's code fixed.
- The projects, by the traits that entry names, each checked at the clone and swapped if it lacks its trait: `express`, `chalk` and `lodash` (small libraries), `eslint` (large), `nest` (application framework with examples), `node-canvas` (a native addon), a project with a registry of its own in its `.npmrc`.
- Held back, run only at the end: `webpack-cli`, `prettier`, `mocha`.
