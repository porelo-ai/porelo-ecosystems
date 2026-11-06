# EC16 — A Project Whose Parent Went Unread Still Gets A JDK

**Status:** Idea.
**Size:** S - `ToolNeeds` and its tests in the Maven module; the contract does not change.

**What must be true.** A person whose multi-module Maven project inherits from a parent outside the checkout, one not in
their local Maven repository, gets a draft whose image runs Maven: it holds a JDK, and the draft says at the parent's
line that the Java release, Maven's version and whatever else the parent sets are unknown until the parent is read.

## Why

A multi-module project's root names a parent with an empty `<relativePath/>`, kept in an organization's repository
manager behind credentials. The parent sets the Java release, typically as `<release>${some.level}</release>` in its
compiler plugin and a `requireJavaVersion` in its enforcer, both filled from a property the root may override. Measured on
an invented fixture of that shape (`org.example`, a root with two modules and a property the parent binds), with the
plugin built from `main`:

- **Parent not in the local repository.** `detect` answers `java.version` unknown ("no pom read asks for a Java release,
  and one that went unread may") at `pom.xml` line 0, `maven.version` empty, `package.maven`, and the parent as unknown
  at its own line. No JDK package is named. `resolve` then turns the empty `maven.version` into the newest Maven,
  unpacked under `/opt/maven` with `MAVEN_HOME`, and names no JDK either. The unpacked Maven brings no Java, so the
  image holds a Maven that cannot start. This is why Maven does not run in
  the task.
- **Parent in the local repository.** The same checkout answers the Java release the root's property sets, at the
  parent's compiler line, its JDK package, and the Maven range of the parent's enforcer, all with origin machine.
  Inheritance, the root's override of a parent's property and the modules are read right.

So the Java release is not lost through the modules or the properties: it lives only in the parent, and an unread
parent leaves no JDK at all. A property in the root alone cannot be read as a Java release, since only the parent says
what it binds; guessing from its name or value would break the rule that nothing is guessed.

Only the person's local caches are read while detecting, and the parent sits behind credentials whose values the plugin
never reads, so fetching the missing parent is not proposed.

## Plan

- `ToolNeeds.findings`: when no Java is stated and a pom went unread, keep `java.version` unknown, and name a JDK
  package beside it, marked as standing in for what the unread parent asks: Maven runs on a JDK, which is known even
  when its release is not. Which JDK is the open question below.
- The unknown `java.version`, the empty `maven.version` and the stand-in JDK stand at the line of the parent that went
  unread, not at `pom.xml` line 0, and their note names that parent and that building the project once on the person's
  machine fills the local repository, after which a new detection answers exactly.
- The same holds for a JDK when a parent is read but its chain breaks further up: the first unread ancestor's line is
  named.
- Red first in `MavenEcosystemTest`: a root with modules and a parent with an empty `relativePath` not in the local
  repository answers a JDK package; today it answers none.

## Acceptance

- On the fixture without the parent in the local repository, the draft names a JDK, and an image built from it runs
  `mvn --version`; the unknown values stand at the parent's line.
- With the parent in the local repository, the answer is unchanged.

## Open questions

- Which JDK stands in when a parent went unread: the highest JDK packaged (proposed; it compiles every release from 8
  up and fails only an upper limit or an older plugin that refuses a new JDK), or the distribution's default JDK, as when
  nothing asks for one.

## To be checked

- Where a first start's note on an imported BOM it could not read comes from: the plugin answers no finding for an
  import, since its first phase stops before imports, as measured on the fixture.
- That Porelo's draft shows a stand-in JDK as something for the person to confirm, as it shows any unknown value.
