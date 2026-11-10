# EC13 — The Maven Adapter Judged By Real Builds

**Status:** Review.
**Size:** L - a corpus of open source Maven projects built in real tasks, and a fix for each fault the Maven adapter shows there; how far it goes is read from the first projects.

**What must be true.** For an open source Maven project, what the Maven adapter answers, written as a project file with no handwork, lets `mvn install` or `./mvnw install` run in a real task, unless the project cannot build outside it or the answer cannot be known offline.

## Plan

- **The corpus:** about ten projects, small libraries, large ones, an application, one with a Maven extension and one with a repository of its own, cloned at a pinned commit into a temporary folder on the test machine.
- **The run:** a build of each project without Porelo first, as the reference, which also fills the account's Maven home as a person's own earlier build would. Then the adapter's Java classes, not the native executable, answer Porelo's `project init`; a task with the stub agent starts from that file; and the build runs in it, `install -DskipTests` first and with tests after.
- **The sort:** a refused host, a wrong JDK or a missing tool is the adapter's. A project that fails its reference, or a test needing a service, is not counted against it.
- **A fix changes only the Java code,** from what the repository says, never by a project's name, and never by editing the written file. About a third of the projects are held back and run only at the end.
- **The stop:** what a reader cannot know offline, such as a host a test asks for at run time, gets no fix and is written down as not recognizable.
- **The first fix, `ToolNeeds.readJava`:** the compiler's `release` only names the bytecode's level, and a JDK from that release up can compile it. The JDK chosen meets every limit a build states: the compiler's level as a lower limit, and each enforcer rule's whole range, upper limit too. The test that goes red first has a model whose two values differ.

## Acceptance

- Every project's result is written down with its pinned commit: built, or why not, in the sort above.
- A fault of the adapter is fixed with a test seen to fail first, or named with why it stays.
- The held back projects are run last, and their result stated apart from the others.

## Results so far

The first runs came before a project's tools were installed in their exact version: a project with no wrapper that asks Maven 3.9, or a plugin that needs `JAVA_HOME`, stopped on that known gap, not counted as a new fault. The reruns of `commons-lang`, `commons-io`, `objects4j` and `gson` ran on Porelo 0.5.1 snapshot 454, whose first start writes `porelo.yml` and adds the checkout to the project `default`.

| Project | Commit | Result |
|---|---|---|
| `spring-petclinic` | `500158f` | Built, tests included |
| `jackson-databind` | `2abab9e` | Built, tests included |
| `utils4j` | `8a2b2bc` | First run built with `-DskipTests`; `testJreFile` failed without Porelo too. Rerun, to try the corpus script after its reading: built, tests included |
| `commons-lang` | `e547d70` | First run stopped: the known gap, Maven 3.8.7 against `[3.9,)`. Rerun: built, tests included |
| `commons-io` | `aa916ea` | First run stopped: the known gap, the same. Rerun: built with `-DskipTests`; 8 of 7213 tests fail, each reaching a host at run time, not recognizable offline |
| `objects4j` | `8e330ac` | First run stopped: the known gap, `JAVA_HOME` for `jdeps`. Rerun: built with `-DskipTests`, no gap; only `CurrencyXmlAdapterTest.testUnmarshalErrorJaxb` fails, without Porelo too |
| `jenkins-update-center-generator-maven-plugin` | `81a7b0f` | Not supported: `docker-maven-plugin`, also without Porelo |
| `porelo-parent` | `e3bf01e` | Porelo's own `porelo.yml` leaves `JAVA_HOME` set; Core's `B453` |
| `guava` | `592a1e6` | Stopped: `toolchains-maven-plugin` asks `api.foojay.io`; no warning yet |
| `mapstruct` | `211b2be` | First run stopped: a Javadoc link's host. Rerun after the fix: built, tests included |
| `dropwizard` | `a8a09d8` | First run stopped: `pgpverify-maven-plugin`'s key servers. Rerun after the fix: built, tests included |
| `logbook` | `b8604cc` | New, chosen by its traits: built, tests included |
| `fastjson2` | `35111f3` | New, chosen by its traits: built, tests included, after two faults of the run itself |
| `spring-data-commons` | `0d270c2` | New, chosen by its traits: stopped, see below |
| `shiro` | `0a1740c` | Not counted: integration tests need Firefox, and run under `-DskipTests`, also without Porelo |
| `gson` | `65df6e5` | Not counted: `protobuf-maven-plugin` fails on Debian's Maven without Porelo. Rerun: the task starts with Maven 3.10.0, and `test-shrinker`, which builds in the reference, stops on a repository that redirects, see below |

The held back projects, `guava`, `mapstruct` and `dropwizard`, built 0 of 3 as a first run. The two reruns are stated as reruns.

## Notes

- **A repository that redirects:** `repo.spring.io` hands an artifact over to a storage host no file names, `jfrog-prod-use1-shared-virginia-main.s3.amazonaws.com`, and `maven.google.com` hands `gson`'s `r8` over to `dl.google.com`. Nothing offline can read that. Opening the host is untried, so no warning says to.
- **`<mirrorOf>*` against a plugin's hosts:** a mirror shuts a pom's repositories out, and not a Javadoc link or a key server. These still reach the person's question, marked as named by the checkout alone.
- **The wording of an unknown:** one for a plugin's link still says "repository".
- **An `hkp://` server or another port:** the host opens; whether the firewall lets that port through is unread.
- **The run's own faults:** a reference with no UTF-8 locale, where a task has `LANG=C.UTF-8`, stopped on a file name; and a second `install` without `clean` stopped `moditect` on its own jar. The test stage now runs `clean install`; projects built before that were not rerun for it.
- **Hosts asked and not opened:** builds in a task also asked the resolver for `repository.apache.org`, and a native build for `repo1.maven.org` and `oss.sonatype.org`, which the draft does not open. They passed without them; who names them is unread. The reruns add `repository.jboss.org` for `objects4j` and `spdx.org` for `commons-lang`, whose license plugin falls back to its cache.
