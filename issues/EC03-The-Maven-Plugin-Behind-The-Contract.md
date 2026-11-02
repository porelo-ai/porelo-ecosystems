# EC03 — The Maven Plugin Behind The Contract

**Status:** Accepted; blocked by the contract in Porelo, [EC01](README.md#ec01) and [EC02](README.md#ec02).

**What must be true.** The Maven plugin is a native executable, in its own package, that Porelo finds at run time: it answers `Detect` over the `Ecosystem1` contract with the tools a checkout needs (the JDK, Maven, the system packages) and the rules ([EC02](README.md#ec02)'s hosts, memory, credential ids), every value with its file and line. `Configure` follows `porelo`'s broker.
