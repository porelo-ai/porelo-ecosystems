# EC09 — A Project's Tools Installed In The Exact Version

**Status:** Idea.
**Size:** M here, by the spike below: the table, the new answer and its tests; the stage that uses it is the image builder's.

**What must be true.** What a project asks is installed in the exact version: the JDK, Maven and any other recognized tool, so that a distribution's packages no longer decide it and a pom asking Maven 3.9 no longer meets 3.8.7.

- A tool manager such as SDKMAN is used only while building: a build stage installs and fetches, and the final image holds only the artifact and its paths, with no `sdk`, no `sdkman-init` and no installer.
- The paths are set correctly: `JAVA_HOME`, `JDK_HOME`, `JRE_HOME`, `MAVEN_HOME`, `PATH`, and for GraalVM `GRAALVM_HOME`.
- `native-maven-plugin` is recognized and brings GraalVM with `native-image` and every library it needs, so such a build works without a hand-written snippet.
- A person signs exact versions: what they read in `project.yml` is what gets built.
- `mvnw` keeps priority for Maven.

## What the spike measured

On a throwaway Ubuntu VM, on `ubuntu:24.04`, with real images and real builds; root podman, as the rootless runs there could not resolve names through a second container. Run in the VM, not through Porelo: the stage is hand-written here.

| Point | Result |
|---|---|
| A stage with SDKMAN, `COPY --from=tools` of Temurin 25.0.1, Maven 3.9.15 and GraalVM CE 25.0.2 | Green. Built in 2m12s, 1.16 GB. |
| The manager is absent from the final image | Green: no `sdk`, no file named `*sdkman*`, no variable. |
| Paths for user `agent`, in `bash -c` and `sh -c`, with `ENV` alone | Green: `JAVA_HOME`, `JDK_HOME`, `JRE_HOME`, `MAVEN_HOME`, `GRAALVM_HOME`, `PATH`. |
| The same stage from vendor URLs and digests, no SDKMAN | Green: 72 s, 1.16 GB, same versions. A wrong digest stops the build (`FAILED`, exit 1). |
| Hello World in the box, only `repo.maven.apache.org` through a proxy | Green: 21 s, on the final image with no package added. A direct connection is refused. |
| Native build on that final image | Red, as expected: `'gcc' not found`. |
| Native build with `gcc`, `libc6-dev`, `zlib1g-dev` | Green: 1m34s with `JAVA_HOME` on Temurin and `GRAALVM_HOME` set, 1m07s with `JAVA_HOME` on GraalVM. The executable runs. The proxy saw no host but Maven's. |

**What the final image lacks.**

- No library a JDK, Maven or `native-image` needs at start: `ldd` finds all on the bare base.
- `ca-certificates` is not needed: the JDK's own store reached Maven over TLS with `/etc/ssl/certs` empty.
- For a native build, `gcc`, `libc6-dev` and `zlib1g-dev`, which the plugin names as packages.
- `fontconfig` and `fonts-dejavu-core` for AWT: without them `drawString` fails headless with "Fontconfig head is null". The plugin cannot see this in a pom, so it stays a note and the person's `package.<name>`.
- `unzip` for a script-only wrapper with a digest, as measured for the packages.

**Other paths.** Maven 3.9.15's `mvn` never reads `M2_HOME` and finds `MAVEN_HOME` itself, so none is needed beyond the list above. Nothing Maven runs reads `JDK_HOME` or `JRE_HOME`; they are set for what else may.

**SDKMAN, read in its code and asked of its service.**

- The installer has no pin: `get.sdkman.io` names a CLI version in its text, and fetches that zip with no digest, testing only that it unpacks.
- Its checksum step runs only for headers the service sends. The service answered none for Temurin, GraalVM CE, Maven or Gradle, and `sdk install maven` printed no verification. So those archives are unchecked with the setting on.
- The service redirects to the vendor: the same GitHub and Maven Central URLs fetched here directly. It adds a host the build must reach, and a mapping.
- Old versions: its list shows the newest of a major, and it still redirects `25.0.1-tem`, which the list no longer shows. `11.0.2-tem` and `8.0.292-tem` answer 404; whether they ever existed under those names was not read. No promise of keeping them was found. Asked by HEAD, identifiers with a `+` answer 404 while `sdk install` installs them.
- Its candidates hold Java, Maven and Gradle, but no Node, Python or Flutter.

**Vendors.** Each names a digest beside the archive: Temurin `.sha256.txt`, GraalVM CE `.sha256`, Maven `.sha512`, Gradle `.sha256`, Node `SHASUMS256.txt`, Flutter a sha256 in `releases_linux.json`. Older Temurin 25.0.1 and Maven 3.0.5 still answer. The Python builds' digests were not read.

## Plan

**No SDKMAN: one way is a fetch by URL and digest.** It is the same small stage for every tool and every plugin, reaches the three plugins SDKMAN cannot, adds no host, and is checked where SDKMAN checks nothing. What SDKMAN would add is the mapping from a version to a URL, which a table holds.

**The exact version comes from a table in the plugin.** A plugin reaches no network, and a table is read, reviewed and shipped with the package. It maps a major to one exact JDK, a Maven line to one exact Maven, each with URL, digest and shape; the agent moves it as it moves any pin. A version it lacks is an unknown naming what was asked, for the person to write. An online step at `project init` was weighed: it would be fresher, but would need a second network path beside a rule that says none, and what a person signs is the same exact version either way.

**The answer.** Beside `package.<name>`, a new list in `Detection`, since one string does not hold it:

    tools: [{name, version, url, sha256, shape, strip, env: {NAME: relative path}, path: [relative paths],
             file, line, origin, note}]

For Maven: `java` (`env` `JAVA_HOME`, `JDK_HOME`, `JRE_HOME`; `path` `bin`), `maven` unless a wrapper fetches it (`MAVEN_HOME`), and with `native-maven-plugin` `graalvm` (`GRAALVM_HOME`). `java.version` and `maven.version` stay as what was asked; `package.openjdk-*` and `package.maven` go, and `package.gcc`, `package.zlib1g-dev` and `package.libc6-dev` stay.

**The form in `project.yml`**, under `image:`, where a person reads and signs it:

    tools:
      - name: java
        version: 25.0.1+8
        url: https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.1%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.1_8.tar.gz
        sha256: 8daf77d1aacffe38c9889689bc224a13557de77559d9a5bb91991e6a298baa0d
        strip: 1
        env: {JAVA_HOME: ., JDK_HOME: ., JRE_HOME: .}
        path: [bin]

Porelo reads `name` as a label, so it names no build tool: its stage fetches, checks, unpacks under one directory, and sets what is written. The image's build must reach those hosts.

**Fit of the other plugins, by reading.** Gradle fits: its wrapper already names a digest, and the JDK is the same row. npm fits with Node's archive. pip fits with a standalone Python build, whose digest is still to be read. Flutter fits for its SDK, while its Android SDK and the packages a native wheel or `node-gyp` needs stay `package.<name>` or unknown.

**A native build's JDK.** Both ways were green; GraalVM alone would be one tool less where its major equals the release, while a project whose JDK and GraalVM differ needs both. The plugin answers both unless the operator chooses otherwise.

## Open questions

- Whether SDKMAN is dropped for the fetch by URL and digest, as recommended above against the wish for one tool manager.

## To be checked

- That the image builder's stage takes this form, and whether a task's user keeps `ENV`, through a real task.
- What a real task's egress must name while the image is built.
- That the table's Maven and JDK rows stay current: how they are moved, and by what.
- The Python builds' digests.
