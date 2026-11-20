# EC31 — The Build's Maven Cache Follows What It Resolves

```json
{
  "$schema": "issue-1.json",
  "id": "EC31",
  "title": "The Build's Maven Cache Follows What It Resolves",
  "state": "Review",
  "size": "XS",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "The CI's Maven cache is saved again whenever a Build fetched something new, so no Build fetches from Central what an earlier Build already fetched."
}
```

**Why this size.** The shared form of the Maven cache, copied into one composite action, one new action and three
workflows; no code and no contract.

**What must be true.** A Build whose poms did not change fetches nothing it fetched before: the cache it restores holds
what the last Build resolved, also when the parent, an imported BOM or a snapshot moved without any pom here changing.

## Plan

- **The cause, measured in the Build logs and the cache list:** `.github/actions/pinned-jdk/action.yml` caches the
  runner's local Maven repository with `actions/cache` under `maven-<os>-<hashFiles('**/pom.xml')>`. The poms have not
  changed since the cache was saved, so every Build restores that exact key, and `actions/cache` never saves over an
  exact hit ("Cache hit occurred on the primary key …, not saving cache"). What moved since is fetched again by every
  Build: the scheduled check after the API snapshot moved fetched its `.pom` and `.jar` from the snapshot repository,
  and each Build fetches the metadata of `bcprov-jdk18on` and `bcutil-jdk18on` from Central, the mark of a version
  range.
- **The cache, in the shared form:** `pinned-jdk` restores under a key holding the run's id and attempt, with the
  poms' hash and then the bare prefix as restore keys, and touches a marker; a new `.github/actions/maven-cache-save`
  saves under that key, with `if: always()`, when a jar or pom changed status after the marker (`find -cnewer`, since
  Maven dates a downloaded file by the server's Last-Modified). Each of the three workflows has one job, which ends
  with it.
- **The range** is gone with the parent: its build tools resolve Bouncy Castle by a fixed version, so nothing is
  pinned here.
- **Red first:** the scheduled check on `main` as it is logs `Downloaded from` for an artifact an earlier Build already
  fetched.

## Acceptance

- Two Builds of the same commit in a row: the second downloads no jar or pom and saves no cache; `-U` still asks the
  snapshots' metadata, on purpose.
- After a snapshot or the parent moves, the next Build fetches it and saves the cache once; the one after fetches
  nothing.
- The first Build after the change logs "fetched after the restore" and saves; the next logs "nothing fetched after
  the restore: nothing saved".
