# EC18 — The Tests Follow The Test Pyramid

```json
{
  "$schema": "issue-1.json",
  "id": "EC18",
  "title": "The Tests Follow The Test Pyramid",
  "state": "Estimated",
  "size": "M",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "The tests follow the test pyramid: every merge case of poms and settings asserted in a unit test, the comparison with real Maven moved out of the unit stage so it runs in under a minute, and the install lines a tool gets in the image run in a test."
}
```

**Why this size.** two fixes in the Maven module's tests, one S and one M, together about a day; no contract and no main
code changes.

**What must be true.** Every flow of the Maven adapter is proven by unit tests over fakes, the module's unit suite runs in
under a minute, and only what nothing lower can show runs as an integration test, in the native executable or in a real
task.

## The audit

Measured on the host, on `644cf86`, with the pinned GraalVM: `clean verify` took 3:33 min, `-Pnative verify` of the Maven
module 95 s, of which 64 s built the image. Measured again on `c5cb097`, alone on the host, with the help plugin already in
the local repository: `clean test` of the Maven module took 67 s, 430 tests, of which `RealMavenComparisonTest`'s 31 took
46 s. Only `adapters/maven` holds code and tests; `shared`, `gradle`, `npm`, `pip` and
`flutter` are empty modules, with no code and no tests.

| Level | Suites | Tests | Share | Run time |
|---|---|---|---|---|
| Unit | `MavenEcosystemTest`, `UnusualPomsAndSettingsTest`, `ToolDownloadsTest`, `RepositoryHostsTest`, `ToolResolverTest`, `WrapperPropertiesTest`, `MavenEcosystemResolveTest`, `NullMarkedTest`, 16 of `VendorHttpTest`'s | 382 | 88 % | 30 s |
| Integration | `RealMavenComparisonTest` (real Maven, one process per case), 3 of `VendorHttpTest`'s (a loopback socket), `NativeExecutableIT` (the built executable, `-Pnative` only) | 38 | 9 % | 161 s, 1 s, 0.6 s after the image |
| End-to-end | `corpus/`: one open source project built in a real task from Porelo's real first start; by hand, not in a workflow | 16 projects | 4 % | 3 to 11 min each, read from the results' folder times |

The shares hold. The run time does not: `RealMavenComparisonTest` runs in Surefire, so the module's unit stage takes 67 to
190 s, depending on the machine's load, and real Maven is at least two thirds of it; without it, about 21 s.

**Flows tested only above the unit level:**

- **How Maven merges poms, parents, BOMs, profiles and settings into repositories:** 31 cases in `MergeFixtures`; 3 of
  them also assert their hosts in `RepositoryHostsTest`, and the other 28 are proven only by the comparison with real
  Maven.
- **The install lines a tool gets in the image** (`ToolLines`: fetch, check the digest, unpack, link): unit tests assert
  their text; that they run, stop on a wrong digest and leave a project's `java` in front, is seen only in a corpus
  build in a real task.
- **The whole draft, from a checkout to a build in a task:** only `corpus/`, by design; each fault it found
  (a Javadoc link's host, `pgpverify-maven-plugin`'s key servers, Testcontainers) is reproduced by a unit test in
  `MavenEcosystemTest`.

**Tests that wait, sleep or reach the network:** none where a fake would do. `VendorHttpTest` waits at most 300 ms, for
its own server's stopped body. `RealMavenComparisonTest` resolves `maven-help-plugin` from the local Maven repository and
reaches Maven Central only when it is missing there; as the oracle of real Maven it has no fake.

## Plan

- **S - Each merge case asserts its hosts in a unit test.** `MergeFixtures` gets beside each case the id and URL set real
  Maven answers, read once from the comparison's output; a parameterized unit test in `RepositoryHostsTest` asserts
  `RepositoryHosts` against it. `RealMavenComparisonTest` becomes `RealMavenComparisonIT`, run by Failsafe in `verify`,
  and still holds the same sets against real Maven, so a recorded set that drifts from Maven goes red there. Red first:
  a merge rule broken in `RepositoryHosts` turns the unit test red without real Maven.
- **M - The install lines run.** An integration test runs what `ToolLines.install` and `installBeside` write against a
  small archive served from a loopback server: a good digest installs and links, a wrong one stops before `tar`, a
  name a broken link holds stops `ln`, a tool beside another leaves the project's `java` first. Red first: the digest
  check taken out of `ToolLines`. The lines write to `/opt` and `/usr/local/bin`; the test runs them with `bash` on the
  build machine, with both paths swapped for directories under a temporary root, and asserts that the swap found each
  place, so a path added to the lines later is not run against the real machine. No container: it would need an image
  fetched by the test, and the harm guarded - a digest not checked, a link over the project's `java` - shows the same
  under a temporary root. `curl`, `tar`, `sha256sum` and `sha512sum` are on the runner and here. It runs in Failsafe as
  `ToolLinesIT`, in seconds.
- **Test levels.** The merge sets: unit. The comparison with real Maven and the install lines: integration, in `verify`.
  Nothing new goes to the native executable, a VM or the corpus.

## Acceptance

- `./mvnw test` on `adapters/maven` takes under a minute, measured, and every case in `MergeFixtures` is asserted there.
- The comparison with real Maven still runs in `verify` and goes red when a recorded set is changed.
- The install lines' test goes red with the digest check taken out.
