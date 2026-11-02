# EC01 — A Spike Of The Model Builder In A Native Image

**Status:** Review.
**Size:** S - one throwaway program and one page of what was measured; nothing of it stays but the answer.

**What must be true.** It is known, by measurement, whether MIMA (`eu.maveniverse.maven.mima`) serves the Maven plugin as a native image: that its static runtime builds under GraalVM, how large the executable becomes, that its model API gives the effective model with parents, properties, profiles and imported BOMs merged as Maven merges them, that it refuses to fetch when offline, and that a value of the model can be traced to its file and line.

## Plan

A program of its own on a branch, never merged, that reads a given checkout and prints the effective model's repositories.

1. MIMA's static runtime, pinned, built with the pinned GraalVM's `native-image`; the reachability metadata it needs, and how much of it there is.
2. The size of the executable, and the time of one answer on a Porelo repository and on a checkout with a parent reached by `<relativePath>` and an imported BOM.
3. Offline: with the network cut by `unshare --user --map-root-user --net`, a parent present only remotely gives an error that names it, and nothing waits on a connection.
4. File and line: whether the model builder's location tracking gives them for a repository inherited from a parent, and in the person's settings.
5. What `.mvn/extensions.xml` does to it: its extensions must never load.

**What it answers, in the room and in this issue, before it is closed:** use MIMA as planned, or what instead, with what was measured. If a native image is not reachable, what the next-best way is and what it costs.

## The answer

**MIMA serves as a native image, and the bare model builder serves better, so the Maven plugin builds on `org.apache.maven:maven-model-builder` alone**, with a resolver that reads only the local repository. Both run Maven's own model builder, so parents, properties, profiles and imported BOMs merge the same way; what MIMA adds is what this repository's rules forbid or do not need.

Measured with MIMA 3.0.1 (Maven 3.10.0, resolver 2.0.24), `maven-model-builder` 3.10.0 and GraalVM 25.4.4.1.1, `-march=x86-64`, on fixtures and on two Porelo repositories. The program is on the local branch `spike/mima-native`.

| | MIMA, static runtime | Bare model builder |
|---|---|---|
| Executable | 30.6 MB | 17.8 MB |
| Jars on the class path | 32, 4.5 MB | 9, 1.1 MB |
| Image build, two at once | 140 s (96 s alone) | 124 s |
| One answer on a fixture, five runs | 15 to 23 ms | 12 to 17 ms |
| One answer in its own measure, two Porelo repositories | 12 to 19 ms | 10 to 11 ms |
| Metadata besides the 46 model types | 11 types, 7 resources | 5 types, 3 resources |
| HTTP client in the executable (`strings`, matching lines) | 229 | 0 |
| Without metadata | "No RepositorySystem present" | The super POM is not found |
| Writes to the local repository in an offline run | a `.locks` directory | nothing (`strace`) |
| A parent or BOM only on a remote | Error naming its coordinates | Error naming its coordinates, with the file and line that asks for it |
| A file present whose tracking names an unknown repository | "present, but unavailable", until configured otherwise | Read |
| Server password | Taken in: the mirror's repository carries authentication | Its element is skipped |

### What holds for both

- **"No RepositorySystem present" is missing metadata.** The failing run had the local repository and offline set already; the same settings with metadata answer.
- **Metadata traced by the agent is not enough.** A pom's `${project.…}` is evaluated by reflection over the model, so a getter no traced run called fails with `MissingReflectionRegistrationError`, an `Error`. A Porelo repository hit this until all 46 model types had their public methods registered, which added about 130 KB. A plugin must register them all and treat such an `Error` as a refusal naming the file.
- **File and line**: a repository's `id` and `url` carry both when inherited from a parent by `relativePath`, from a parent in the local repository and from a default-active profile, with properties interpolated. Super POM values name an internal resource, not a file.
- **The settings model has no locations.** A hardened StAX walk gives file and line for mirrors, server ids and profile repositories, and runs in the native image. The answer's settings part must be read that way in either variant.
- **A settings profile's repositories are not in MIMA's effective model**, only among its context repositories without a location. The bare variant must inject them itself; not yet run.
- **An imported BOM's repositories do not reach the effective model**, as in Maven; its managed dependencies do. A missing BOM still fails the build of the model.
- **Offline**: without network, through `unshare --user --map-root-user --net`, a remote parent or BOM fails in under 30 ms and nothing waits. On the JVM, with MIMA's offline flag and the network up, a local trap server got no request; with the flag off it got two, so that flag is all that stands between MIMA and the network. Under `strace`, on the checkout with extensions, neither variant connected anywhere but to the local name service cache socket.
- **`.mvn/extensions.xml`** is not opened by either (`strace`), and build extensions in a pom load nothing: the model builds with no request for them. Naming the file is the plugin's own check.
- **A hostile pom**: an external entity and nested entities are refused with the entity's name, and neither the file's content nor an expansion appears; the parser ignores the internal DTD. No size limit exists, so a plugin checks the size first.
- **Interpolation**: `${env.X}`, `${user.name}` and `${settings.servers[0].password}` stay literal in both, with no secret in any output. `${project.basedir}` and `${project.pomFile}` do interpolate, so a url can carry the checkout's local path. `${project.class.classLoader}` stays literal in the native image but expands on the JVM.
- **A `relativePath` leaving the checkout is followed**, and its values enter the model. The plugin must compare each value's file with the checkout and the local repository, and name any other.

### What MIMA would still need

A false unknown on a present file is cured by configuration: preferring the simple local repository manager and turning off the prefix filter together made both cases answer on the JVM; which of the two matters was not separated. Its transport, password handling and `.locks` directory remain.

### What the bare variant costs

The local-only resolver is about 25 lines. It must also find the local repository from the settings, and read a split repository's parts; the spike passed those parts by hand. Mirrors and settings profiles are its own code, not Maven's.

### What it means for what follows

- [EC02](README.md#ec02) builds on the bare model builder: its Plan gains the settings reader, the resolver and the check of a value's file.
- [EC03](README.md#ec03) takes the metadata above and the refusal of a reflection `Error`.
- A value can come from neither the person nor the checkout: a parent in the local repository, or the super POM; the contract's `MACHINE` says so. A checkout with `.mvn/extensions.xml` is reported as an unplaced finding naming the file.

## Acceptance

- The numbers above are written here, each from a run, with the versions used.
- The answer names what [EC02](README.md#ec02) and [EC03](README.md#ec03) build on.
