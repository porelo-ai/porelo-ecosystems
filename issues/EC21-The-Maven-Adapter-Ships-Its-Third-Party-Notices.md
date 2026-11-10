# EC21 — The Maven Adapter Ships Its Third-Party Notices

```json
{
  "$schema": "issue-1.json",
  "id": "EC21",
  "title": "The Maven Adapter Ships Its Third-Party Notices",
  "state": "Backlog",
  "size": "-",
  "priority": "Medium",
  "rank": 3,
  "blockedBy": [],
  "covers": "The Maven adapter's packages carry the full license and notice texts of every library compiled into its native executable, from the shared generator, named in the deb's copyright file and marked as a license in the rpm."
}
```

**How it stands.** Backlog on the operator's word (operator, 2026-11-10): built at this repository's turn, behind its
Highest and High work, with the shared notices generator now published.

**What must be true.** Whoever installs `porelo-ecosystem-maven`, as a .deb or an .rpm, finds beside the package's
own license one `THIRD-PARTY-NOTICES` file with the full license and notice texts of every third-party library compiled
into its executable, and the package's copyright file names each such library with its license.

## Why

The executable is a native image compiled whole from the program and its runtime libraries. Today:

- The rpm ships `/usr/share/licenses/porelo-ecosystem-maven/LICENSE` and `LICENSE.Apache-2.0` as `%license`, and its
  `license` tag says `GPL-3.0-or-later AND Apache-2.0`. The deb ships the executable and its copyright file alone,
  which points to the system's GPL-3 and Apache-2.0 texts.
- `src/deb/copyright/copyright` says the executable is compiled from the program, the classes of `porelo-wire` and
  `porelo-ecosystem-api`, Maven's model libraries and SnakeYAML, under Apache-2.0 beside the program's own license.

The runtime classpath, as `dependency:list` with the runtime scope shows it, holds more, under more licenses:

- Apache-2.0: `maven-model-builder`, `maven-model`, `maven-artifact`, `maven-builder-support`,
  `plexus-interpolation`, `plexus-utils`, `snakeyaml`, `jspecify`, `javax.inject`, `porelo-wire`,
  `porelo-ecosystem-api`.
- EPL-2.0: `org.eclipse.sisu.inject`.
- BSD-3-Clause: `org.ow2.asm:asm`.
- MIT: `slf4j-api`.
- Embedded in `plexus-utils`, under `licenses/` and named in its NOTICE: the Indiana University Extreme! Lab Software
  License 1.1.1, BSD-2-Clause (javolution) and BSD-3-Clause (ThoughtWorks).
- The GraalVM native image runtime, compiled into every native executable.

Six of these jars hold no license or notice text at all (`sisu.inject`, `asm`, `plexus-interpolation`,
`javax.inject`, `snakeyaml`, `jspecify`): their license is named only in their POM and manifest, so the full text
comes from the license itself, not from the jar.

The file is to come from one generator shared by Porelo's packages: given a runtime classpath, it writes one
`THIRD-PARTY-NOTICES` with each library's name, version and license, and the full text of every license and notice
the jars carry or name, embedded ones among them.

## To be checked

- Which of the runtime classpath's classes the native image keeps: a library with no class kept may still be listed.
- The license and notice texts the GraalVM runtime asks of a native executable, as the GraalVM release used here
  ships them.
- Whether the deb ships the texts under `/usr/share/doc/porelo-ecosystem-maven/` beside its copyright file, and the
  rpm `THIRD-PARTY-NOTICES` as `%license` with a `license` tag naming every license in it.
