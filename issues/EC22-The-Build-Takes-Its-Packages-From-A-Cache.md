# EC22 — The Build Takes Its Packages From A Cache

```json
{
  "$schema": "issue-1.json",
  "id": "EC22",
  "title": "The Build Takes Its Packages From A Cache",
  "state": "Implementing",
  "size": "S",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "The Build takes the Ubuntu packages that read the built packages from a cache keyed by their versions, and every cache of the workflows keeps only its newest entry."
}
```

**Why this size.** two workflow files and the `pinned-jdk` action; no code, no contract.

**What must be true.** A Build does not fail because an outside server is down when it fetches something it fetched
before: every download a workflow makes comes from a cache once it has been fetched, and a cache holds only the newest
entry of each kind, so it never fills up.

## Plan

- **What is not cached today.** `.github/workflows/build.yml`, step *What reads the built packages*, runs
  `sudo apt-get update` and `apt-get install rpm rpm2cpio cpio` on every Build, against Ubuntu's mirrors. Everything
  else is cached already: the GraalVM and shfmt by digest, the Maven repository and the wrapper's Maven, in
  `.github/actions/pinned-jdk/action.yml`. The probes of the published indexes in `build.yml` must reach the server and
  stay uncached; the `-U` fetch of Porelo's snapshots is the Maven cache's.
- **The packages.** Their versions are pinned in the step, as everything a build runs is. The `.deb` files go into
  `actions/cache` keyed by those versions; on a hit they are installed with `dpkg -i` and nothing is asked of a mirror,
  on a miss `apt-get download` fetches them first and the cache is saved. Where the runner image already carries a
  package, it is dropped from the list.
- **Only the newest entry.** The Maven repository's key is the hash of every `pom.xml`, so each change of a pom adds
  an entry and the old ones stay until GitHub evicts them. After a save, the workflow deletes the entries of the same
  kind with another key, through the Actions cache API with the job's token (`actions: write`); the same holds for the
  wrapper's Maven, the GraalVM, shfmt and the new packages.
- **Tests.** None: a change to the CI's own workflows, proven by a Build with a cold cache and one with a warm one,
  the second fetching nothing from a mirror.

## Acceptance

- A Build on a warm cache shows no request to an Ubuntu mirror, and installs `rpm`, `rpm2cpio` and `cpio` from the
  cache.
- After a pom changes and a Build runs, the repository's cache list holds one Maven entry, not two.

## To be checked

- Which of `rpm`, `rpm2cpio` and `cpio` the `ubuntu-24.04` image already has.
- Whether the repository settings can delete cache entries after a set time, instead of the delete step; GitHub's
  default drops an entry unused for seven days and holds 10 GB per repository.
