# EC08 — The Maven Plugin Published As A Recommended Package

**Status:** Review.
**Size:** M - one package's metadata, a second format, and a publishing step in the workflow; Porelo's own package changes one line in each of its formats.

**What must be true.** A person who installs Porelo from its package source gets the Maven plugin by default, in a package of its own, and can leave it out or remove it without touching Porelo. It is not part of the interface's package.

## Plan

Read in today's code and in how the agent and message-filter packages are published.

1. **`plugins/maven/src/deb/control/control`** gains `Depends: porelo, libc6 (>= <floor>), zlib1g`: the executable links `libc.so.6` and `libz.so.1` (`ldd`), and `jdeb` does not read that. The floor is measured on the built executable, as Porelo's packages hold theirs.
2. **The version moves.** `0.1.0~SNAPSHOT` never changes, so apt would never offer a newer build; a snapshot takes `~snapshot.<run>`, which sorts below its release and above the run before.
3. **An `.rpm` beside the `.deb`,** written by the Maven plugin the other Porelo packages use, without `rpmbuild`, with the same requirements.
4. **`.github/workflows/build.yml`** publishes from `main` as a snapshot and from a tag `v<version>` as a release, to the same two repositories as Porelo's packages, switched by `PORELO_PUBLISH`; a publish is believed only once the published index shows the exact version. Each package is unpacked and run with an empty environment first, answering `describe`.
5. **This repository's access** to the publication credential and `PORELO_PUBLISH` is an organization setting, verified before the first publishing run.
6. **Porelo's package** names `porelo-ecosystem-maven` under `Recommends` in its `.deb` and `.rpm`, as it names the message filter. Not `Depends`: the plugin depends on Porelo, so that would be a cycle, and it could not be left out.

Red first: a package whose version does not move between two runs, or that does not start with an empty environment, fails the workflow's check.

## Acceptance

- On a clean Ubuntu 24.04 with Porelo's package source, `apt-get install porelo` installs the plugin, `porelo ecosystems` lists it, and `project init` on a Maven checkout recognizes it; so does a first `task start`, once Porelo asks there.
- `apt-get install --no-install-recommends porelo` leaves it out, and `apt-get remove porelo-ecosystem-maven` leaves Porelo installed; an upgrade does not bring it back.
- The same on Fedora with `dnf`, and `--setopt=install_weak_deps=False` leaves it out.
- The published index shows the exact version of a build.

## What apt does, measured

On Ubuntu 24.04, with throwaway packages in a local package source:

- A default install takes a recommended package; `--no-install-recommends` leaves it out, and it can be installed later on its own.
- Removing it leaves the recommending package installed; neither installing that package again nor upgrading it brings it back.
- Removing Porelo takes the plugin with it, through its `Depends`.
- A recommended package not in the index is skipped with exit 0, so the order of publishing breaks nothing; a machine installing in between gets no plugin.
- **The first upgrade to a version that adds the `Recommends`:** `apt-get upgrade` keeps the recommending package back at its old version, while `apt upgrade` and `apt-get dist-upgrade` move it and install the plugin.

So no choice at installation needs anything beyond apt and dnf. A question asked during installation is something neither offers without a package prompt, and is not planned.

## What the built packages do, measured

Built with `-Pnative verify` on the pinned GraalVM, read and installed in clean containers, with a stand-in `porelo` package, since nothing is published yet:

- Ubuntu 24.04: without `porelo`, `apt-get` refuses; with it, both install, `describe` answers with an empty environment, and removing the plugin leaves `porelo`.
- Fedora 44: `rpm -i --test` names only `porelo` as missing, so its C library and zlib requirements hold; installed without that check, it answers the same and erases cleanly.
- The two checks fail as they should: a version not moved names both packages (`check-package-version`), and a floor below what the executable links names the symbol (`check-linkage`). A package that does not answer stops the workflow's step, a `PORELO_PUBLISH` neither true nor false stops it, and a `JF_URL` not `https` stops before the token is sent.

## Decided while building

- **A release that fails between its two uploads is not completed by a rerun:** the rerun finds the first package stored and stops, and a native build's bytes differ, so it could not prove them the same. The release goes out under the next version instead. The exposure is one version number; it stays because a stop is loud and a published release is never replaced, and a release's bytes checked against the store would change the answer.

## To be checked

- The publishing steps' upload, which runs only on the first push to `main`; that needs this repository's access to `JF_ACCESS_TOKEN`, `JF_URL` and `PORELO_PUBLISH`, which an agent's token cannot read.
- The acceptance above against the published index, on Ubuntu and on Fedora, once Porelo's package names this one under `Recommends`.
- What dnf does with a weak dependency: read, not measured.
- Whether the setup script should offer leaving the plugin out: it installs with recommendations on and has no such option, so its users remove the package afterward.
