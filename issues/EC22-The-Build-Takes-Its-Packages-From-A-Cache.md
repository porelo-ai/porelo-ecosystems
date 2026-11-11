# EC22 — The Build Takes Its Packages From A Cache

```json
{
  "$schema": "issue-1.json",
  "id": "EC22",
  "title": "The Build Takes Its Packages From A Cache",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "The Build takes the Ubuntu packages that read the built packages from a cache keyed by their pinned versions, and every cache of the workflows is dropped by GitHub once no run has read it for seven days."
}
```

**Why this size.** one workflow step; no code, no contract.

**What must be true.** A Build does not fail because an outside server is down when it fetches something it fetched
before: every download a workflow makes comes from a cache once it has been fetched, and an entry no run reads any more is
deleted after a set time, so the cache never fills up.

## Plan

- **What is not cached today.** `.github/workflows/build.yml`, step *What reads the built packages*, runs
  `sudo apt-get update` and `apt-get install rpm rpm2cpio cpio` on every Build, against Ubuntu's mirrors. Everything
  else is cached already: the GraalVM and shfmt by digest, the Maven repository and the wrapper's Maven, in
  `.github/actions/pinned-jdk/action.yml`. The probes of the published indexes in `build.yml` must reach the server and
  stay uncached; the `-U` fetch of Porelo's snapshots is the Maven cache's.
- **The packages.** Their versions are pinned in the step, as everything a build runs is, and the cache key is
  `apt-rpm-<os>-<image>-<pins>`. On a miss, `apt-get install --download-only` puts every `.deb` the image lacks into the
  cached directory; in both cases `dpkg -i` installs them from there, and a hit asks no mirror. A tool missing afterwards
  stops the step with what was asked and what was found.
- **Old entries.** Every key carries the version or digest it holds, so a new pin writes a new entry and the old one is
  read by no run again; the Maven repository's `restore-keys` restores only the newest. GitHub deletes an entry no run
  has read for seven days, which keeps the cache from filling up without a deletion step or `actions: write`.
- **Tests.** None: a change to the CI's own workflows, proven by the Build. The step was rehearsed in an `ubuntu:24.04`
  container: a miss downloaded and installed the pins with their dependencies, and a hit installed them with no
  package lists and no network.

## Acceptance

- A Build on a warm cache shows no request to an Ubuntu mirror, and installs `rpm`, `rpm2cpio` and `cpio` from the
  cache.
- The repository's cache list shows the `apt-rpm-` entry after the first Build, and no second one after the next.
