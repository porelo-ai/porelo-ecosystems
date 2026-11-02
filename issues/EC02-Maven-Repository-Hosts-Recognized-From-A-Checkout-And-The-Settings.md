# EC02 — Maven Repository Hosts Recognized From A Checkout And The Settings

**Status:** Implementing.
**Size:** M - one module's library, its answer as plain records, the settings reader, and fixtures compared with real Maven; no contract and no native image.

**What must be true.** Given a checkout and the person's Maven settings, the Maven module answers which repository hosts a build must reach, each with its file and line and whose it is: the person's own settings, the checkout's, which anybody who can push wrote, or the machine's. Parents are merged and properties filled in as Maven does, across every level. It needs nothing of Porelo's contract and is tested without it.

## Plan

Built on `maven-model-builder` alone in `plugins/maven`, package `org.fuin.porelo.ecosystem.maven`. Red first: a repository URL built from a property across three levels, against what real Maven answers.

1. **`RepositoryHosts`** walks the checkout's root pom and its modules, and returns `Answer`: records of its own for a host (id, host, URL without user info, query or fragment, kind, where the repository stands, where each property it used stands, whose it is, the server id and whether it is known), an unknown (what, why, where) and an unplaced finding. The contract's types come later.
2. **The model**: `DefaultModelBuilder` with two-phase building stopped after the first phase, so imports are never read. An import brings only managed dependencies, never a repository or a property, so a missing BOM costs nothing.
3. **Whose a value is** is the least trusted of where the repository stands and where the properties it used stand: a settings repository filled in from a checkout's property is the checkout's.
4. **Nothing of the machine reaches the builder**: no environment and no system property. `${env.X}` stays literal, and a URL still holding `${…}` is an unknown naming the property, never a host read out of half a URL.
5. **Profiles**: only the settings' active ones and a pom's `activeByDefault`. One activated by JDK, OS, file or property depends on where the build runs, so what it holds is an unknown naming the profile and its condition.
6. **`LocalRepositoryResolver`** reads a parent by the repository's layout, its split parts included; coordinates are checked before they form a path, and a parent not found is an unknown naming its coordinates, with the file and line asking for it, and the walk goes on.
7. **`PomGuard`** wraps the model processor, the one place every pom is read: size and count limits, a DTD refused with the file's name, and nothing read outside the checkout and the local repository, symbolic links resolved first.
8. **`SettingsReader`**: hardened StAX with lines for the local repository, mirrors, server ids, and profiles; a password's element is skipped, never held. Settings profiles enter the builder as profiles, so their properties and repositories merge as Maven merges them.
9. **Mirrors** replace what they mirror, as Maven selects them: an exact id first, then the first pattern, with `*`, `external:*`, a list and `!`.
10. **`.mvn/extensions.xml`** is named as an unplaced finding and the answer marked as not trusted; nothing of it is opened.

## Acceptance

- A repository URL from a property defined in the grandparent, overridden in the parent and used in the child, and the reverse, `${project.…}` and `${revision}` are answered as real Maven answers them.
- On the same fixtures, the repositories match `help:effective-pom` from the pinned Maven, in tests only.
- A property from a settings profile fills a URL, and a settings repository filled in from a checkout's property is the checkout's.
- A property nothing defines leaves an unknown naming it and no host; `${env.X}` and a system property the same, seen to fail first with a request carrying the environment.
- A profile activated by JDK or OS is an unknown naming it, with no host taken from it.
- A value names where its repository stands and where its property stands.
- A pom naming a repository host is answered with that host marked as the checkout's, with path and line; a settings mirror is marked as the person's.
- A settings mirror of `*` leaves Maven Central out.
- A settings file with a `<server>` password yields an answer in which the password appears nowhere, seen to fail first with a reader that copies it; a URL's user info appears nowhere either. A server id is answered with whether it is known.
- A pom declaring an external entity that names a local file yields an answer without that file's content, and one with nested entity expansion is refused with its name; both seen to fail first without the guard.
- Coordinates or a `relativePath` leaving the allowed places read nothing outside them, a symbolic link included.
- A checkout with `.mvn/extensions.xml` is named as such and its extension's code never loaded.
- A parent missing locally is named unknown with its coordinates; with the parent present, the value is answered. A BOM missing locally answers exactly as one present, and a repository declared in a BOM or its parent is not answered.

## The answers measured

- **Imported BOMs are not needed.** Against the builder and real Maven, a repository and a property declared in an imported BOM or its parent do not reach the effective model; a Java release set there (17, 11) stays the pom's (25). With two-phase building stopped after the first phase the resolver is not called for the import, and a missing BOM answers the same repositories and properties. Only the managed versions are lost, so a later value needs a BOM only if it is a dependency's managed version; a Java release never does.
- One run of real Maven on a fixture takes about 2.7 s, so the comparison costs seconds per fixture.
