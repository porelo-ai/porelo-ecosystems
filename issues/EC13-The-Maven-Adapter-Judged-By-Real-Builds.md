# EC13 — The Maven Adapter Judged By Real Builds

**Status:** Implementing.
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
