# EC14 — A Toolchain Plugin Is Warned Of

**Status:** Accepted.

**What must be true.** A person whose pom uses a plugin that picks or fetches its own JDK reads a warning at that plugin's line in the draft, instead of meeting a build that stops on a refused host.

- `org.mvnsearch:toolchains-maven-plugin` fetches its own JDK from hosts a draft cannot name: the warning names `api.foojay.io` and `-Dtoolchain.skip`, and no host is opened for it. `guava` at `592a1e6` stops on the refused `api.foojay.io`.
- `maven-toolchains-plugin`: its `<jdk><version>` is read and at least said as a warning; whether the adapter fetches that JDK and writes a `toolchains.xml` is decided later.
