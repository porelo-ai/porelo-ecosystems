# EC16 — A Project Behind An In-House Repository Manager Builds In A Task

**Status:** Idea.
**Size:** L - the JDK stand-in is one class in the Maven module, but the task's settings must carry a mirror that takes
every repository, which changes the contract `configure` is called with, in Porelo's contract library too.

**What must be true.** A person whose multi-module Maven project inherits from a parent kept in an in-house repository
manager, reached through a `settings.xml` whose mirror takes every repository, gets a draft whose task builds:

- The image holds a JDK, so Maven runs, even when the parent is not in the person's local Maven repository. The draft
  then says at the parent's line that the Java release, Maven's version and whatever else the parent sets are unknown
  until the parent is read.
- The task's egress names the mirror's host and no host the mirror stands in for, and every repository the build asks,
  Maven Central and the parent's included, reaches that mirror through the broker.

## Why

A multi-module project's root names a parent with an empty `<relativePath/>`, kept in an organization's repository
manager. The parent sets the Java release, typically as `<release>${some.level}</release>` in its compiler plugin and a
`requireJavaVersion` in its enforcer, both filled from a property the root may override. The person's `settings.xml`
holds one mirror with `<mirrorOf>*</mirrorOf>`, so every repository, Maven Central included, is reached only through that
manager. An always-active profile names the same manager as repository and plugin repository, with update policies for
releases and snapshots. There is no `<servers>` section.

Measured on invented fixtures of that shape (`org.example`, a root with two modules and a property the parent binds, a
mirror at `repo.example.org`), with the plugin built from `main`:

- **Parent not in the local repository.** `detect` answers `java.version` unknown ("no pom read asks for a Java release,
  and one that went unread may") at `pom.xml` line 0, `maven.version` empty, `package.maven`, and the parent as unknown
  at its own line. No JDK package is named. `resolve` then turns the empty `maven.version` into the newest Maven,
  unpacked under `/opt/maven` with `MAVEN_HOME`, and names no JDK either. The unpacked Maven brings no Java, so the image
  holds a Maven that cannot start.
- **Parent in the local repository.** The same checkout answers the Java release the root's property sets, at the
  parent's compiler line, its JDK package, and the Maven range of the parent's enforcer, all with origin machine.
  Inheritance, the root's override of a parent's property and the modules are read right.
- **The mirror that takes every repository.** `detect` answers one host, the mirror's, at its line in the person's
  settings, "with no credential named"; Maven Central is no longer answered, as with real Maven. The profile's
  repository of the same URL adds no host.
- **Settings that are not well-formed XML,** such as attributes without quotes: `detect` refuses the whole checkout as
  unreadable, naming the file and line.

So the Java release is not lost through the modules or the properties: it lives only in the parent, and an unread parent
leaves no JDK at all. A property in the root alone cannot be read as a Java release, since only the parent says what it
binds, and nothing is guessed. Only the person's local caches are read while detecting, so fetching the missing parent
is not proposed.

In the task, what `configure` writes today is one mirror per repository, its `<mirrorOf>` that repository's name, and a
profile naming each repository. A repository Porelo passes for the mirror therefore takes only itself: Maven Central
keeps its own URL and is refused by the egress, which names only the mirror's host. The task builds only when its
settings hold a mirror of `*` to the broker's URL for the in-house manager, as the person's do. The always-active
profile is then not needed in the task: with a mirror of every repository, Maven's own Central reaches the manager too.

## Plan

- **The stand-in JDK.** `ToolNeeds.findings`: when no Java is stated and a pom went unread, `java.version` stays
  unknown, and the highest JDK the distribution packages stands beside it, marked as standing in for what the unread
  parent asks. Maven runs on a JDK, which is known even when its release is not, and the highest JDK compiles every
  release from 8 up; it fails only an upper limit or a plugin that refuses a new JDK, which reading the parent settles.
- The unknown `java.version`, the empty `maven.version` and the stand-in stand at the line of the parent that went
  unread, not at `pom.xml` line 0. Their note names that parent and says that building the project once on the person's
  machine fills the local repository, after which a new detection answers exactly. Where a parent is read but its chain
  breaks further up, the first unread ancestor's line is named.
- **The mirror's reach.** `detect` answers what a mirror takes beside its host: a rule finding `mirror.<id>` with its
  `<mirrorOf>` as written, at its line. The contract's `Repository` carries what a repository mirrors, empty for itself
  alone, and `configure` writes it as the `<mirrorOf>`. This is a contract change, agreed with Porelo's contract library
  before it is built, and carried through `project.yml` there.
- **The credential.** With no `<servers>` entry for the mirror, the answer names no credential, as today: the person's
  build sends none from its settings, so the task asks for none from them.
- **The image's Maven.** Where the person's settings mirror every repository, the Maven `resolve` fetches is taken
  through that mirror at the same path, with its digest read from the same mirror, so a network that reaches only the
  mirror still builds the image.
- Red first in `MavenEcosystemTest`: a root with modules and a parent with an empty `relativePath` not in the local
  repository answers a JDK package, where today it answers none; a mirror of `*` makes `configure` write a mirror of `*`,
  where today it writes the repository's name.

## Acceptance

- On the fixture without the parent in the local repository, the draft names a JDK, and an image built from it runs
  `mvn --version`; the unknown values stand at the parent's line.
- With the parent in the local repository, the answer is unchanged.
- In a task made from the draft with an invented mirror of every repository, a build resolves a parent only that mirror
  holds and an artifact of Maven Central, both through the broker, and the egress names no host but the mirror's.

## To be checked

- With the task's settings written as planned, against a local stand-in repository manager: that the mirror of `*`
  alone, without the profile, resolves the parent and Central's artifacts, by real Maven.
- How the person's build is let into its repository manager when its settings name no server: anonymously, by the
  network it runs in, or through a proxy; the answer decides whether the task's broker forwards a credential.
- Whether Porelo's image build reaches the vendors' hosts on a network that reaches only the mirror, and where the
  stand-in JDK's package comes from there.
- Where a first start's note on an imported BOM it could not read comes from: the plugin answers no finding for an
  import, since its first phase stops before imports, as measured on the fixture.
- That Porelo's draft shows the stand-in JDK as something for the person to confirm, as it shows any unknown value.
