# EC18 — The Tests Follow The Test Pyramid

**Status:** Accepted.
**Size:** M - two fixes in the Maven module's tests, one S and one M; no contract and no main code changes.

**What must be true.** Every flow of the Maven adapter is proven by unit tests over fakes, the module's unit suite runs in
under a minute, and only what nothing lower can show runs as an integration test, in the native executable or in a real
task.

## The audit

Measured on the host, on `644cf86`, with the pinned GraalVM: `clean verify` took 3:33 min, `-Pnative verify` of the Maven
module 95 s, of which 64 s built the image. Only `adapters/maven` holds code and tests; `shared`, `gradle`, `npm`, `pip` and
`flutter` are empty modules, with no code and no tests.

| Level | Suites | Tests | Share | Run time |
|---|---|---|---|---|
| Unit | `MavenEcosystemTest`, `UnusualPomsAndSettingsTest`, `ToolDownloadsTest`, `RepositoryHostsTest`, `ToolResolverTest`, `WrapperPropertiesTest`, `MavenEcosystemResolveTest`, `NullMarkedTest`, 16 of `VendorHttpTest`'s | 382 | 88 % | 30 s |
| Integration | `RealMavenComparisonTest` (real Maven, one process per case), 3 of `VendorHttpTest`'s (a loopback socket), `NativeExecutableIT` (the built executable, `-Pnative` only) | 38 | 9 % | 161 s, 1 s, 0.6 s after the image |
| End-to-end | `corpus/`: one open source project built in a real task from Porelo's real first start; by hand, not in a workflow | 16 projects | 4 % | 3 to 11 min each, read from the results' folder times |

The shares hold. The run time does not: `RealMavenComparisonTest` runs in Surefire, so the module's unit stage takes about
190 s, three quarters of it real Maven.

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
  check taken out of `ToolLines`.

## Acceptance

- `./mvnw test` on `adapters/maven` takes under a minute, measured, and every case in `MergeFixtures` is asserted there.
- The comparison with real Maven still runs in `verify` and goes red when a recorded set is changed.
- The install lines' test goes red with the digest check taken out.

## To be checked

- Whether the install lines run in a container (Podman, on the runner too) or with their paths taken from a root a test
  can set; `/opt` and `/usr/local/bin` are written into them today.
