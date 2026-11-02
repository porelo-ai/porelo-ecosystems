# EC02 — Maven Repository Hosts Recognized From A Checkout And The Settings

**Status:** Review.
**Size:** M - one module's library, its answer as plain records, the settings reader, and fixtures compared with real Maven; no contract and no native image.

**What must be true.** Given a checkout and the person's Maven settings, the Maven module answers which repository hosts a build must reach, the same hosts the developer's real build would reach, each with the file and line of its URL and where that stands: the person's own settings, the checkout, or the machine. Parents are merged and properties filled in as Maven does, across every level. It needs nothing of Porelo's contract and is tested without it.

## The decision that changes this issue

A plugin [has nothing to secure](../doc/decisions.md#a-plugin-has-nothing-to-secure): the aim is the right answer, and no hostile checkout is hardened against. What the build reads is input to the answer; what cannot be followed stays an unknown naming it. The global settings and the proxies go in with [EC03](README.md#ec03).

## Plan

Built on `maven-model-builder` alone in `plugins/maven`, package `org.fuin.porelo.ecosystem.maven`. Red first: a repository URL built from a property across three levels, against what real Maven answers.

1. **`RepositoryHosts`** walks the checkout's root pom and its modules, and returns `Answer`: records of its own for a host (id, host, URL without user info, query or fragment, kind, the line of its URL, where that stands, the server id and whether it is known), an unknown (what, why, where) and an unplaced finding. The contract's types come later.
2. **The model**: `DefaultModelBuilder` with two-phase building stopped after the first phase, so imports are never read. An import brings only managed dependencies, never a repository or a property, so a missing BOM costs nothing.
3. **A value comes from the builder, and names the line of its `<url>`.** Where that line stands is its origin: `CHECKOUT` in the checkout, `YOURS` in the person's settings under the home, and `MACHINE` otherwise. Where a property that filled it in stands is not followed, so a settings repository filled in from a checkout's property is `YOURS`.
4. **Of the machine, only what the checkout gives the build reaches the builder.** `${env.X}` and a system property stay literal, since the box decides them, and a URL still holding `${…}` is an unknown naming the property, never a host read out of half a URL.
5. **Profiles**: the settings' active ones, a pom's named by the settings' active ids or by `-P`, a pom's `activeByDefault`, and one on a property or a file in the checkout, which are evaluated. One on a JDK, an OS, the environment, a system property or a file outside the checkout stays off, and is an unknown naming the profile and its condition. A settings profile on any condition stays such an unknown.
6. **`LocalRepositoryResolver`** reads a parent by the repository's layout, its split parts included; coordinates are checked before they form a path, and a parent not found is an unknown naming its coordinates, with the file and line asking for it, and the walk goes on.
7. **`PomGuard`** wraps the model processor, the one place every pom is read: size and count limits, and a DTD refused with the file's name, since nested entities make a small pom huge. A parent or module beside the checkout is read, as the build reads it. A pom on which the builder gives up, or that outgrows the memory, is an unknown naming it, and the walk goes on.
8. **`SettingsReader`**: StAX with lines for the local repository, mirrors, server ids, and profiles; a password's element is skipped, never held. Values are filled in from what the checkout gives the build, as Maven fills in settings when it reads them. Settings profiles enter the builder as profiles, so their properties and repositories merge as Maven merges them.
9. **Mirrors** replace what they mirror, as Maven selects them: an exact id first, then the first pattern, with `*`, `external:*`, `external:http:*`, a list and `!`, each only for a layout it names, a URL taken apart as Maven takes it. A mirror is selected before the URL it replaces is looked at.
10. **`.mvn/maven.config` and `.mvn/jvm.config` are input.** `MavenOptions` reads `maven.config` one argument per line: `-D` goes to the user properties, `-P` to the profiles, `-s` replaces the settings, and `-Dmaven.repo.local` the local repository. `jvm.config`'s `-D` goes to the system properties. An option that changes the model and cannot be followed, such as `-gs` or `-javaagent`, is named as an unplaced finding with its line. **Extensions are never loaded:** `.mvn/extensions.xml`, whose content is never opened, and an extension a pom loads, at any level or in a profile on a condition, are named as unplaced findings too.
11. **What the build never reaches is no host:** a repository with both policies off, and a blocked mirror, which is named. **What Maven would fill in or refuse in the settings** is named too: a `${…}` in what a mirror covers, in an active profile or in the local repository, and a mirror lacking a URL or what it mirrors. A mirror whose pattern asks a value that could not be filled in is answered with an unknown naming it.

## Acceptance

- A repository URL from a property defined in the grandparent, overridden in the parent and used in the child, and the reverse, `${project.…}` and `${revision}` are answered as real Maven answers them.
- On the same fixtures, the repositories match `help:effective-pom` from the pinned Maven, in tests only.
- A property from a settings profile fills a URL, a property in `.mvn/maven.config` wins over a pom's, one in `.mvn/jvm.config` does not, and both fill in the settings, as real Maven answers.
- A property nothing defines leaves an unknown naming it and no host; `${env.X}` and a system property the same.
- A profile activated by JDK, OS or the environment is an unknown naming it, with no host taken from it; one on a property or a file in the checkout answers as real Maven answers.
- A value names the file and line of its URL.
- A pom naming a repository host is answered with that host marked as the checkout's, with path and line; a settings mirror is marked as the person's.
- A settings mirror of `*` leaves Maven Central out.
- A settings file with a `<server>` password yields an answer in which the password appears nowhere, seen to fail first with a reader that copies it; a URL's user info appears nowhere either. A server id is answered with whether it is known.
- A pom declaring an external entity that names a local file yields an answer without that file's content, and one with nested entity expansion is refused with its name; both seen to fail first without the guard.
- A parent or module beside the checkout is read, as real Maven reads it; coordinates that cannot form a path in a local repository are named.
- A pom that doubles its values through any field, or that breaks the builder, is named and leaves its siblings answered.
- A checkout with `.mvn/extensions.xml` is named as such and its extension's code never loaded.
- A parent missing locally is named unknown with its coordinates; with the parent present, the value is answered. A BOM missing locally answers exactly as one present, and a repository declared in a BOM or its parent is not answered.

## The answers measured

- **Imported BOMs are not needed.** Against the builder and real Maven, a repository and a property declared in an imported BOM or its parent do not reach the effective model; a Java release set there (17, 11) stays the pom's (25). With two-phase building stopped after the first phase the resolver is not called for the import, and a missing BOM answers the same repositories and properties. Only the managed versions are lost, so a later value needs a BOM only if it is a dependency's managed version; a Java release never does.
- One run of real Maven on a fixture takes about 2.7 s, so the comparison costs seconds per fixture.
- **Maven applies a settings profile `activeByDefault` beside an explicit one**, measured against real Maven: a test here assumed it stands back, and the comparison refuted it.
- **Maven takes a parent with an empty `relativePath` from the local repository, even where the checkout's own reactor holds it**, measured against real Maven 3.9.15; the answer does the same.
- **A settings profile `activeByDefault` does not activate a pom's profile of its id, nor stop a pom's default one**, measured against real Maven; that fixture is part of the comparison.
- **`.mvn/maven.config` and `.mvn/jvm.config` each redirect a repository** of the person's settings through a property, since Maven fills in settings when it reads them, measured against real Maven 3.9.15; `-Dmaven.repo.central` in `jvm.config` moves nothing there.
- **Each line of `maven.config` is one argument** in Maven 3.9.15, as measured: `-s ci/settings.xml` on one line names a file with a space in its name, and Maven stops; on two lines it reads the file.
- **The bound on filling in was not needed for an answer:** a pom doubling through 40 levels, and 100,000 uses of a 1 MiB value, end in under two seconds with a 1 GiB heap, as a refusal naming the pom. Not measured in a native image.
- **On six of Porelo's own checkouts** the answer takes 4 to 303 ms and holds the hosts their poms and settings name; the one unknown is `porelo`'s profile on `!env.GITHUB_RUN_ID`.

## Declined from the review

- **A `<module>` from a settings profile named at the wrong pom:** a settings profile has no modules, in Maven's model nor here.
- **An empty `<mirrorOfLayouts/>` meaning `default,legacy`:** both of Maven's selectors, read in its source, treat empty as any layout, as here.
- **`anExactIdWinsOverAnEarlierPattern` proving nothing:** a selection without the exact id first turned it red when broken so.
- **A reactor parent looked up in the checkout:** real Maven takes the local copy, as measured above.
- **The comparison run offline:** a first run on a clean machine fetches the help plugin it compares with, and offline it could not start.

## Open

- **Builder and comparison differ in version:** the module builds on `maven-model-builder` 3.10.0 and compares with the wrapper's Maven 3.9.15, so what only 3.10 does has no reference.
- **Credentials:** a `ServerReference` matches by id alone, while Maven 3.10 can hold a credential back from a host not declared for that server.
- **Read since [EC03](README.md#ec03):** the settings' proxies and the global settings of a Maven installation.
- **A settings profile on a property or a file** stays an unknown, though a pom's is evaluated.
- **A `-D` or `-P` on the command line** is not seen: it is not in any file.
