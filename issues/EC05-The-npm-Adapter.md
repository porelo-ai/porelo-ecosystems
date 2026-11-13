# EC05 — The npm Adapter

```json
{
  "$schema": "issue-1.json",
  "id": "EC05",
  "title": "The npm Adapter",
  "state": "Analyzed",
  "size": "M",
  "priority": "Low",
  "rank": 4,
  "blockedBy": ["EC24"],
  "covers": "A checkout built with npm gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line."
}
```

**Why this size.** one module reading `package.json`, `.nvmrc`/`.node-version`, the lockfile and both `.npmrc` levels, `Configure` as an `.npmrc`, and a corpus of real builds; no contract change.

**What must be true.** A checkout built with npm gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. It reads `package.json`, the lock file and the workflow's `setup-node`.

## Plan

- **A module of its own, `adapters/npm`**, on the shared answer and packaging. A directory without `package.json` is
  `NotRecognized`.
- **Tools:** Node from `.nvmrc`, `.node-version`, then `engines.node`; npm, pnpm or yarn from `packageManager` and the
  lockfile present; Node installed in its exact version by the shared download, as the JDK is. Packages for native addons
  (`node-gyp`: `python3`, `make`, `g++`) when a lockfile names `node-gyp` or a package with `gypfile`.
- **Rules:** registry hosts from the checkout's `.npmrc` (from the checkout) and the person's user `.npmrc` and the global
  `npmrc` (yours, machine), scoped registries included, and the hosts the lockfile's `resolved` URLs name; an auth
  token is named by its registry key only.
- **`Configure`** writes an `.npmrc` pointing each registry at the broker, no token.
- **Level:** unit tests over fixture checkouts; the corpus as the acceptance. **Red first:** an `.npmrc` with
  `_authToken` answers no token anywhere, seen red with a reader that copies values.

## Acceptance

- Judged by real builds, as `doc/decisions.md` says under *An adapter is judged by real builds*: a real build with `npm` runs in a real task from the file Porelo's first start writes, with only this adapter's code fixed.
- The projects, by the traits that entry names, each checked at the clone and swapped if it lacks its trait: `express`, `chalk` and `lodash` (small libraries), `eslint` (large), `nest` (application framework with examples), `node-canvas` (a native addon), a project with a registry of its own in its `.npmrc`.
- Held back, run only at the end: `webpack-cli`, `prettier`, `mocha`.
