# EC09 — A Project's Tools Installed In The Exact Version

**Status:** Idea.
**Size:** M - the online step, its answer as lines and values, their tests; it changes a contract, and the image builder needs no new stage.

**What must be true.** What a project asks is installed in the exact version: the JDK, Maven and any other recognized tool, so that a distribution's packages no longer decide it and a pom asking Maven 3.9 no longer meets 3.8.7.

- Ordinary `image.snippet` lines fetch each tool by URL and digest, with no tool manager, and the image holds only the artifact; `image.env` and links on the path do the rest.
- When `project.yml` is made, the newest stable version of what the project asks is found: the newest 25 for Java 25, the newest stable Maven in the pom's range, GraalVM likewise. Pre-releases never count. Offline or on a vendor's failure, the draft says unknown and names what was asked.
- `project.yml` holds the download URL and digest of the version found when the file is made, and the image's build checks it. From then on it is the person's: the plugin only bootstraps.
- The paths are set correctly: `JAVA_HOME`, `JDK_HOME`, `JRE_HOME`, `MAVEN_HOME`, the tools on the path, and for GraalVM `GRAALVM_HOME`.
- `native-maven-plugin` is recognized and brings GraalVM with `native-image` and every library it needs, so such a build works without a hand-written snippet.
- `mvnw` keeps priority for Maven.

## What the spike measured

On a throwaway Ubuntu VM, on `ubuntu:24.04`, with real images and real builds; root podman, as the rootless runs there could not resolve names through a second container. Run in the VM, not through Porelo: the stage is hand-written here.

| Point | Result |
|---|---|
| A stage with SDKMAN, `COPY --from=tools` of Temurin 25.0.1, Maven 3.9.15 and GraalVM CE 25.0.2 | Green. Built in 2m12s, 1.16 GB. |
| The manager is absent from the final image | Green: no `sdk`, no file named `*sdkman*`, no variable. |
| Paths for user `agent`, in `bash -c` and `sh -c`, with `ENV` alone | Green: `JAVA_HOME`, `JDK_HOME`, `JRE_HOME`, `MAVEN_HOME`, `GRAALVM_HOME`, `PATH`. |
| The same stage from vendor URLs and digests, no SDKMAN | Green: 72 s, 1.16 GB, same versions. A wrong digest stops the build (`FAILED`, exit 1). |
| Plain lines in the image, no build stage, rootless on a developer machine rather than the VM, with the newest at that time: Temurin 25.0.5, Maven 3.10.0, GraalVM CE 25.0.5.0.3; `env` as Porelo sets it, links in `/usr/local/bin` | Green: 79 s, 1.37 GB with the native packages, all commands for `agent` in `bash -c` and `sh -c`; Hello World and a native build run. |
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

**A fetch by URL and digest, no SDKMAN, in ordinary lines.** The same small `RUN` serves every tool and plugin, needs no new field and no stage in the image builder, reads as what it does, and the person can change it. It reaches what SDKMAN cannot, adds no host of its own, and checks what SDKMAN leaves unchecked.

**The online step, a call of its own in the contract.** Reading stays as it is: `detect` needs no network and answers what is asked. A new `resolve`, a varlink method of its own beside `detect`, takes those asks and asks the vendors once, when the file is made: Porelo calls it only for `project init`, never for `task start`, and shows it in the draft as its own step, naming each host. It is the one place in the plugin with an HTTP client. The contract's sentence that a plugin opens no connection changes with it; today no `resolve` exists and the executable holds no HTTP client.

| Tool | Host asked | What it answers, measured |
|---|---|---|
| Temurin | `api.adoptium.net` | `assets/latest/<major>/hotspot`: one row with the exact version, link and sha256. An unknown major answers an empty list with status 200, which must read as unknown. |
| GraalVM CE | `api.github.com` | `repos/graalvm/graalvm-ce-builds/releases`: tag, `prerelease` and each asset's `digest`. 60 requests an hour per address without a token, so a shared address can get a refusal, which reads as a vendor's failure. |
| Maven | `repo.maven.apache.org` | `maven-metadata.xml`, then the archive's `.sha512`. |

**The tags decide less than they seem to.** GraalVM's newer releases are tagged `graal-25.5.5.0.3` and named `graalvm-community-jdk-25i5-25.0.5.0.3_…`, so a filter on `jdk-25` stops at 25.0.2. The major is read from the asset's name, in both forms. Foojay's service was weighed as one source for all JDKs: on the day that release appeared it still answered the one before, while the vendor's list had it.

**Pre-releases are filtered, never taken from the source's own word.** `maven-metadata.xml` names `4.0.0-rc-7` as both `latest` and `release`, and the newest stable among its 90 versions is 3.10.0. A version is stable only without a qualifier; a range is applied after that filter, since `[3.9,)` holds the release candidates too.

**The digest comes from the same source as the archive.** It catches a broken or changed download from then on, and of the first sight proves no more than the TLS connection it was read over.

**The answer.** `detect` keeps `java.version` and `maven.version` as what was asked. `resolve` answers ordinary findings under two more kinds of key, which Porelo places by their prefix alone, so it names no build tool: `snippet.<name>`, whose value is the lines, joined in the order of the key's name, each with its source as a comment, and `env.<NAME>`, whose value is the variable's. Two plugins giving one key two values are a disagreement, as today.

For Maven: `snippet.java`, `snippet.maven` unless a wrapper fetches it, and with `native-maven-plugin` `snippet.graalvm`; `env.JAVA_HOME`, `env.JDK_HOME`, `env.JRE_HOME`, `env.MAVEN_HOME`, `env.GRAALVM_HOME`. `package.openjdk-*`, `package.default-jdk-headless` and `package.maven` go; `package.gcc`, `package.zlib1g-dev` and a new `package.libc6-dev` stay.

**What `project.yml` then holds**, under the repository's `image:` (`repositories.<name>.image`, where the draft writes its image lines today), written once and then the person's:

    snippet: |
      RUN set -eu; mkdir -p /opt/java; curl -fsSL -o /tmp/java.tgz '<url>'; echo '<digest>  /tmp/java.tgz' | sha256sum -c -; tar xzf /tmp/java.tgz -C /opt/java --strip-components=1; rm /tmp/java.tgz; ln -s /opt/java/bin/* /usr/local/bin/
    env:
      JAVA_HOME: /opt/java

**`PATH` cannot be set this way:** `image.env` refuses it, as Porelo sets it in every task, and an `env.PATH` is refused too. So each tool's commands are linked into `/usr/local/bin`, which stands on that path. From a second tool holding the same names, such as GraalVM's `java`, only what it adds is linked.

**What a vendor answered goes into lines a build runs as root**, so `resolve` writes a URL, version and digest only when each holds what it should: `https`, a known host, a plain character set, a hex digest. Anything else is an unknown.

**Each line assumes `curl`, which Porelo's image holds, and `tar` and `sha256sum` on a glibc base.** A `base_image` without them fails loudly at that line, and the person sees why.

**What the image's build reaches**, differing from the step's hosts: `github.com` and its redirect `release-assets.githubusercontent.com`, and `repo.maven.apache.org`.

**What the plugin process needs.** Porelo starts a plugin with an empty environment and no network namespace, so nothing blocks the step, and it would reach no proxy: `resolve` needs the person's proxy settings passed in, for this call alone: `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` and their lower-case forms. The executable gains `https` and an HTTP client, whose size is not measured.

**Fit of the other plugins, by reading.** Gradle fits: its wrapper already names a digest, and the JDK is the same row. npm fits with Node's archive. pip fits with a standalone Python build, whose digest is still to be read. Flutter fits for its SDK, while its Android SDK and the packages a native wheel or `node-gyp` needs stay `package.<name>` or unknown.

**A native build's JDK.** Both ways were green; GraalVM alone would be one tool less where its major equals the release, while a project whose JDK and GraalVM differ needs both. Each answers its own lines.

## To be checked

- That these lines and `env` work through a real task and the image builder, as they ran under plain `podman` only.
- That a snippet taken whole from another source replaces the draft's lines, as the file's order of sources says.
- The size the HTTP client adds to the executable.
- That GraalVM's asset names read the same for its next releases, and the Python builds' digests.
- The first run's times through the real step: only single requests were timed.
