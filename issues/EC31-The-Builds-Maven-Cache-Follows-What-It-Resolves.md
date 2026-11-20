# EC31 — The Build's Maven Cache Follows What It Resolves

```json
{
  "$schema": "issue-1.json",
  "id": "EC31",
  "title": "The Build's Maven Cache Follows What It Resolves",
  "state": "Backlog",
  "size": "S",
  "priority": "High",
  "rank": 1,
  "blockedBy": [],
  "covers": "The CI's Maven cache is saved again whenever a Build resolves something new, so no Build fetches from Central what an earlier Build already fetched, and no version range is resolved online on every Build."
}
```

**Why this size.** One composite action of the CI and the pom's dependency management; no code and no contract.

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
- **The cache:** split it into `actions/cache/restore` with the restore key `maven-<os>-` and `actions/cache/save` after
  the build, under a key made from the repository's content once the build has run (the sorted list of files under the
  runner's local Maven repository with their sizes, hashed), skipped when that key is the one restored. A Build that
  fetched nothing saves nothing; one that fetched something saves once.
- **The range:** find which dependency asks `bcprov-jdk18on` and `bcutil-jdk18on` by a range and pin the version in
  the root pom's dependency management, so it is resolved from the cache.
- **Red first:** the scheduled check on `main` as it is logs `Downloaded from` for an artifact an earlier Build already
  fetched.

## Acceptance

- Two Builds of the same commit in a row: the second logs no `Downloaded from` and saves no cache.
- After a snapshot or the parent moves, the next Build fetches it and saves the cache once; the one after fetches
  nothing.
- No Build logs `Downloaded from` for `maven-metadata.xml` of a pinned release.

## To be checked

- Which dependency brings the `bcprov-jdk18on` and `bcutil-jdk18on` range, and whether the pin belongs here or in the
  parent.
