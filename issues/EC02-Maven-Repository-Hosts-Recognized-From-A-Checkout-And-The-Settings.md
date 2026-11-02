# EC02 — Maven Repository Hosts Recognized From A Checkout And The Settings

**Status:** Backlog; blocked by [EC01](README.md#ec01).
**Size:** M - the reading of the effective model and the settings, its answer as plain records, and the hostile-file tests; no contract and no native image.

**What must be true.** Given a checkout and the person's Maven settings, the Maven module answers which repository hosts a build must reach, each with its file and line and whose it is: the person's own settings, or the checkout's, which anybody who can push wrote. It needs nothing of Porelo's contract and is tested without it.

## Plan

Sized before the code is read, so this is adjusted after [EC01](README.md#ec01).

1. A library class in `plugins/maven` with its answer as records of its own: a host, its URL, file, line, origin, and a credential's id where one is named. The contract's types come later, in [EC03](README.md#ec03).
2. From the effective model: `<repositories>` and `<pluginRepositories>`, parents and imported BOMs included.
3. From the person's settings: mirrors, which replace what they mirror, and the `<server>` ids; a password is never read into anything the answer carries.
4. A parent missing from the local repository is an unknown value that names its coordinates, never a guess. A checkout with `.mvn/extensions.xml` is named, its extensions never loaded, and the model marked as not trusted.
5. Hardened parsing, as this repository's rules say.

## Acceptance

- A pom naming a repository host is answered with that host marked as the checkout's, with path and line; a settings mirror is marked as the person's.
- A settings mirror of `*` leaves Maven Central out.
- A settings file with a `<server>` password yields an answer in which the password appears nowhere; seen to fail first with a reader that copies it.
- A pom declaring an external entity that names a local file yields an answer without that file's content, and one with nested entity expansion is refused with its name; both seen to fail first with the parser's defaults.
- A checkout with `.mvn/extensions.xml` is named as such and its extension's code never loaded; with the network cut, nothing reaches out, and a parent only on a remote repository is named unknown.
- A parent missing locally is named unknown with its coordinates; with the parent present, the value is answered.

## To be checked

- Whether profiles active by default, and a settings profile's repositories, are read as Maven reads them.
