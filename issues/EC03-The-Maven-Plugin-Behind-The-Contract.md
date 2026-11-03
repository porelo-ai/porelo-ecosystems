# EC03 — The Maven Plugin Behind The Contract

**Status:** Review.
**Size:** M - one class behind the contract, a reader of what a task's image needs, the settings a task's Maven reads, the global settings and proxies, and the executable with its package.

**What must be true.** The Maven plugin is a native executable, in its own package, that Porelo finds at run time: it answers `Detect` over the `Ecosystem1` contract with the tools a checkout needs (the JDK, Maven, the system packages) and the rules (the repository hosts, memory, credential ids), every value with its file and line. `Configure` follows `porelo`'s broker. It is built on the plain model builder, registers the public methods of all Maven model types for reflection, since a pom's `${project.…}` reaches them, and turns a reflection `Error` into a refusal naming the file. It never reads a password: a server is named by its id, with whether it is known.

**What it adds to the recognition of repository hosts.** The settings of a Maven installation and the settings' proxies go in here. A `-s` file is placed by where it lies: in the checkout `CHECKOUT`, under the home `YOURS`, elsewhere `MACHINE` with an absolute path.

## Plan

1. **`MavenEcosystem`** implements the contract and is the executable's main. A directory without a `pom.xml` is `NotRecognized`, settings that cannot be read are `Unreadable`, and everything else stays an answer.
2. **The keys.** The contract names none, and the merge reads two values under one key as a disagreement, so what can come several times has a key per value. Tools: `java.version`, `maven.version`, `package.<name>`. Rules: `host.<host>`, `credential.<id>`, `memory.maven`, `memory.tests`. What cannot be followed: `unknown.<what>`, unknown and with no value.
3. **`ToolNeeds`** reads each pom's effective model. The JDK from the compiler's configuration, its management included, then the properties, then the enforcer's rule; the highest of all modules, since only that JDK builds them all. Maven from the wrapper, then the enforcer's rule, then the prerequisites, and a pom asking nothing still needs one. The wrapper's host is a host, as the build goes there first. Packages from a short table of plugins known to run a program, and what `exec-maven-plugin` runs; a script of the checkout's and the JDK's own `java` need none. Memory from `-Xmx` in `.mvn/jvm.config` and in the tests' `argLine`.
   **What the image installs is answered as packages too**, since Porelo names no build tool and takes only `package.<name>` into the image; `java.version` and `maven.version` stay what was asked. The JDK is the lowest of Ubuntu 24.04's that compiles the release, `openjdk-<n>-jdk-headless` (8, 11, 17, 21, 25, as measured on Porelo's default image), `default-jdk-headless` when every pom was read and nothing asks one, an unknown above them all, and none when the release cannot be filled in or a pom or parent that went unread may ask one. Maven is `package.maven` unless a wrapper with its `mvnw` fetches it. A script-only wrapper with a digest and a `.zip` needs `package.unzip`: on that image, without `unzip`, it fetches the tarball, which fails the zip's digest, measured; with `unzip` and a JDK it runs, with neither `curl` nor `wget`, and without a digest it needs no `unzip`.
4. **A host's credential** is a finding only when the settings name that server; a repository without one needs nothing stored. A proxy with a user is named the same way, by its id.
5. **`Configure`** writes `.m2/settings.xml`: a repository and plugin repository per entry in an active profile, and a mirror of its name, so a pom naming the same id reaches the broker too. It holds no credential, so it is not owner-only. No repository, no file.
6. **Global settings**: `-gs` from `.mvn/maven.config`, and otherwise the installation the wrapper unpacked under the home. Merged under the person's as Maven merges them: by id theirs gives way, and an active id in one file activates a profile in the other. Each value keeps its own file.
7. **Proxies**: an active one is a host, as the build goes through it; `nonProxyHosts` is read and not applied, so a host behind the proxy is still answered as a host.
8. **The executable**: under `-Pnative`, the model's types registered with all public methods, and a test holds that list against the jar. The metadata repository of the build tools is off, so the image holds this list alone. A type left out leaves the value unfilled, and so an unknown; an `Error` from the builder is a refusal naming the pom. The package installs `/usr/libexec/porelo/ecosystems/maven`.

## Acceptance

- Over its socket the executable answers exactly what the code answers on the JVM, for a checkout whose pom walks the model by reflection, and it calls itself by the name it is installed under.
- Each key above is answered with its file and line on fixtures, and a password or a proxy's user name appears nowhere.
- The global-settings fixtures and the file `Configure` writes match `help:effective-pom` from the pinned Maven.
- The package holds the executable at the path Porelo looks in.

## Open

- **An `Error` from the builder** was never seen in a run: leaving a type out gave an unknown instead, so that catch is untested.
- **The keys are this plugin's, until Porelo's merge names them:** it is not built yet, and may ask for others.
- **Which Maven's global settings count:** the plugin starts with an empty environment and knows no installation but the wrapper's; a Maven installed elsewhere, such as `/etc/maven/settings.xml`, is not read, and the build in its box has its own anyway.
- **Proxies are not compared with real Maven:** `help:effective-pom` does not show them.
- **The package is a `.deb` only**; an `.rpm` and a place to publish them are not built.
- **The table of packages** names Debian's packages, and what a plugin runs beyond it stays unseen.
- **The JDK and Maven packages are Ubuntu 24.04's:** a project's other `base_image` may lack one or hold another, and Ubuntu's Maven there is 3.8.7, which a pom's enforcer rule may refuse; the note says what was asked.
- **`native-maven-plugin` needs GraalVM's `native-image`,** which no package named here holds, so such a build gets its `gcc` and `zlib1g-dev` and still fails in the image until the person's `snippet` brings it.
