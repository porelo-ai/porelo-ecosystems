# EC21 — The Maven Adapter Ships Its Third-Party Notices

```json
{
  "$schema": "issue-1.json",
  "id": "EC21",
  "title": "The Maven Adapter Ships Its Third-Party Notices",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 3,
  "blockedBy": [],
  "covers": "The Maven adapter's packages carry the full license and notice texts of every library compiled into its native executable, from the shared generator, named in the deb's copyright file and marked as a license in the rpm."
}
```

**Why this size.** one module's build and its two package descriptions, one test, no contract; the generator is
the shared one.

**How it stands.** Backlog on the operator's word (operator, 2026-11-10); taken at once on the coordinator's word, built
with the shared notices generator now published.

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

## Plan

- **The runtime classpath, written in every build**: `dependency:build-classpath` with the runtime scope writes
  `target/classpath.txt` before the tests, for the test below and for the generator.
- **The notices, in the native build at `prepare-package`, before the image**: the CycloneDX plugin writes
  `target/bom.json` (the module is not deployed, so `skipNotDeployed` is off), then `porelo-release
  third-party-notices` writes `target/THIRD-PARTY-NOTICES` from the classpath, the bill and the JDK the image is built
  with, so a refusal costs no image. The bill needs Maven online: a clean offline build has none, and the generator
  refuses, so no package is made without the file; a bill left from an earlier build is held to every jar's hash.
- **The deb** ships it as `/usr/share/doc/porelo-ecosystem-maven/THIRD-PARTY-NOTICES`; its copyright file names each
  library with its license, says where the full texts are, and gives a `License` paragraph for every license named.
- **The rpm** ships it as `/usr/share/licenses/porelo-ecosystem-maven/THIRD-PARTY-NOTICES`, marked `%license`, and
  its `license` tag names `GPL-3.0-or-later AND Apache-2.0 AND EPL-2.0 AND BSD-3-Clause AND MIT AND BSD-2-Clause AND
  xpp AND GPL-2.0-only WITH Classpath-exception-2.0`: the program's, the libraries', what plexus-utils embeds, and
  the runtime's.
- **Tests, each seen red first**: `CopyrightTest` holds the copyright file to the runtime classpath: every jar on it
  is named, read from its file name whatever its version carries, the file says where the full texts are, and the
  executable's licenses stand on one `License` line, which a parser reads whole.

## Acceptance

- The native build writes the file with no refusal: 14 jars and the runtime.
- `dpkg-deb -c` lists the file beside the copyright file; `rpm -qpL` lists it with the two licenses before; `rpm -qp
  --qf '%{LICENSE}'` gives the tag above.
- `CopyrightTest` fails for a jar on the classpath that the copyright file does not name.

Measured on a local native build:

- **Classes kept:** the executable holds class names of every library but `asm` and `jspecify` (annotations only);
  both stay listed, as the generator lists every jar of the classpath, never less than the image needs.
- **The runtime's texts:** the GraalVM release's `LICENSE.txt`, `LICENSE_NATIVEIMAGE.txt`, `THIRD_PARTY_LICENSE.txt`
  and its whole `legal/` directory stand in the file's last section.
