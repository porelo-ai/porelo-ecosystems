# EC03 — The Maven Plugin Behind The Contract

**Status:** Accepted; blocked by the contract in Porelo and [EC02](README.md#ec02).

**What must be true.** The Maven plugin is a native executable, in its own package, that Porelo finds at run time: it answers `Detect` over the `Ecosystem1` contract with the tools a checkout needs (the JDK, Maven, the system packages) and the rules ([EC02](README.md#ec02)'s hosts, memory, credential ids), every value with its file and line. `Configure` follows `porelo`'s broker. It is built on the plain model builder, registers the public methods of all Maven model types for reflection, since a pom's `${project.…}` reaches them, and turns a reflection `Error` into a refusal naming the file. It never reads a password: a server is named by its id, with whether it is known.
