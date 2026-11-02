# EC01 — A Spike Of The Model Builder In A Native Image

**Status:** Backlog.
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

## Acceptance

- The numbers above are written here, each from a run, with the versions used.
- The answer names what [EC02](README.md#ec02) and [EC03](README.md#ec03) build on.

## To be checked

- All of the above: nothing here has been run yet.
