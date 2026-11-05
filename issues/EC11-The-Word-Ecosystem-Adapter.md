# EC11 — The Word Ecosystem Adapter

**Status:** Backlog.
**Size:** M - seven modules, the workflow and every text move; no behavior changes and no contract.

**What must be true.** Whoever reads this repository, or what it ships, finds "ecosystem adapter", and "Maven adapter" for this one, and no longer "plugin" for what Porelo finds at run time, since that word reads as a Maven plugin. It holds before a first release fixes the word.

## Plan

Read in today's tree, in the published package 0.1.0~snapshot.5 and in the contract 0.5.1-SNAPSHOT.

1. **`plugins/` becomes `adapters/`,** with its submodules. `plugins/pom.xml`'s artifactId `porelo-ecosystems-plugins` becomes `porelo-ecosystems-adapters`, in that pom, in the six that name it as parent, and in the root's `<module>`; a grouping module is never published.
2. **`.github/workflows/build.yml`:** its paths to `plugins/maven/target` move.
3. **What a person reads:** the `README.md` of the root and of each module, the poms' descriptions, `AGENTS.md`'s own part, `doc/decisions.md`, the `.deb`'s control file and the `.rpm`'s description in `plugins/maven/pom.xml`, and both unreleased changelog sources.
4. **Issues:** the titles, file names and text of the open issues, and the index.
5. **Code comments and Javadoc** that call this program a plugin, such as those in `MavenEcosystem`.

**The word stays where it means Maven's own:** `pluginRepository`, `RepositoryHost.Kind.PLUGIN_REPOSITORY`, `org.apache.maven.model.Plugin`, `exec-maven-plugin`, and every text about a plugin in a pom. So this is read line by line, never replaced in bulk.

**What stays, and costs a person nothing:**
- The package `porelo-ecosystem-maven`, its Java package and its artifactId.
- The installed paths: `/usr/libexec/porelo/ecosystems/maven` and the folder under `$XDG_DATA_HOME/porelo/ecosystems` hold no such word, so an upgrade moves nothing.
- The contract `org.fuin.porelo.Ecosystem1`: the word stands only in six comment lines of its description, in no method, type or field. Its answers and `describe` hold none, and the program's own strings name it nowhere.

**What this repository does not hold:** the field `plugin` in `porelo project init --json`, and the comments in the contract's description, belong to Porelo. This issue reads neither.

No test goes red first: nothing can do harm that the packaging checks do not already cover. The workflow's whole build, its check of each package and the native tests show that the move broke nothing.

## Acceptance

- A search for "plugin" in tracked files finds only what means Maven's own, and each remaining match has been read.
- The workflow's steps run green on a clean build under the new paths, and the two packages hold the same files at the same paths as before and answer `describe`.
- `check-readmes`, `check-citations` and `check-shared` are green.

## To be checked

- Whether another repository links to `plugins/` here, or to the anchor `#a-plugin-has-nothing-to-secure` in `doc/decisions.md`, whose title changes with the word.
