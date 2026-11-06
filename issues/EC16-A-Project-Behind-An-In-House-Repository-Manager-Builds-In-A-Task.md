# EC16 — A Project Behind An In-House Repository Manager Builds In A Task

**Status:** Implementing.
**Size:** M - four parts in the Maven module (the JDK stand-in, settings that do not parse, the forms of a credential,
a mirror that takes every repository) and one contract change, the mirror's reach `configure` is called with; about 90
minutes, against a comparable issue of another repository estimated at 120 that took 43.

**What must be true.** A person whose multi-module Maven project inherits from a parent kept in an in-house repository
manager, reached through a `settings.xml` whose mirror takes every repository, gets a draft whose task builds:

- The image holds a JDK, so Maven runs, even when the parent is not in the person's local Maven repository. The draft
  then says at the parent's line that the Java release, Maven's version and whatever else the parent sets are unknown
  until the parent is read.
- The task's egress names the mirror's host and no host the mirror stands in for, and every repository the build asks,
  Maven Central and the parent's included, reaches that mirror through the broker.
- Where the manager needs a credential, the draft names it at its server's line, bound to the mirror's host, and says
  what the person stores in the Porelo-Vault: what kind it is, and whether Porelo can take it as the settings hold it.
  No secret's value is decrypted, held or written anywhere.
- A `settings.xml` that does not parse does not stop detection: the draft warns at that file, with the line and column of
  the parse error and none of its content, and the rest is answered without that file.

## Why

A multi-module project's root names a parent with an empty `<relativePath/>`, kept in an organization's repository
manager. The parent sets the Java release, typically as `<release>${some.level}</release>` in its compiler plugin and a
`requireJavaVersion` in its enforcer, both filled from a property the root may override. The person's `settings.xml`
holds one mirror with `<mirrorOf>*</mirrorOf>`, so every repository, Maven Central included, is reached only through that
manager. An always-active profile names the same manager as repository and plugin repository. There is no `<servers>`
section, and attribute values stand without quotes, so the file does not parse.

Measured on invented fixtures of that shape (`org.example`, a root with two modules and a property the parent binds, a
mirror at `repo.example.org`), with the plugin built from `main`:

- **Parent not in the local repository.** `detect` answers `java.version` unknown at `pom.xml` line 0, `maven.version`
  empty, `package.maven`, and the parent as unknown at its own line. No JDK package is named. `resolve` turns the empty
  `maven.version` into the newest Maven, unpacked under `/opt/maven`, and names no JDK either. That Maven brings no Java,
  so the image holds a Maven that cannot start.
- **Parent in the local repository.** The same checkout answers the Java release the root's property sets, its JDK
  package and the parent's Maven range. Inheritance, the root's override of a parent's property and the modules are read
  right: the release lives only in the parent, and an unread parent leaves no JDK at all. A property in the root alone
  cannot be read as a Java release, since only the parent says what it binds, and nothing is guessed.
- **The mirror that takes every repository.** `detect` answers one host, the mirror's, "with no credential named"; Maven
  Central is no longer answered, as with real Maven.
- **The task's settings.** `configure` writes one mirror per repository, its `<mirrorOf>` that repository's name. A
  repository Porelo passes for the mirror takes only itself, so Maven Central keeps its own URL in the task and is refused
  by the egress.
- **Settings that do not parse.** `detect` refuses the whole checkout as unreadable. Maven 3.9.9 stops on the same file
  with "Non-parseable settings", so a build that runs on the person's machine does not read that file; the warning says
  so.

Only the person's local caches are read while detecting, so fetching the missing parent is not proposed.

### How Maven reads a credential

- A credential is a `<server>` in the user's settings (`settings.xml` in the Maven folder of the home, or `-s`) or the
  global ones (the installation's `conf/settings.xml`, or `-gs`), the user's winning by id. Maven takes the server whose id is the
  repository's, or the mirror's where a mirror takes the repository.
- A server holds `<username>` and `<password>` (Basic authentication), or `<privateKey>` and `<passphrase>`, or a token
  sent as a header through `<configuration><httpHeaders><property><name>`/`<value>`; it may hold several.
- Maven fills `${env.NAME}` and `${property}` into the settings before it reads them, so a password can live in an
  environment variable.
- Maven 3 encrypts a password written `{…}` with a master password kept in `settings-security.xml` (beside the user's
  settings, or named by `-Dsettings.security`), which itself is only obscured by a key Maven ships. Maven 4 encrypts with
  a master password it takes from a source configured in `settings-security4.xml`, written `{[…]…}`.

Porelo does not decrypt either form: decrypting would put the secret into the plugin, which never holds a credential's
value. Detection names an encrypted secret as such and tells the person to store the plain value in the Porelo-Vault.

## Plan

- **The stand-in JDK.** `ToolNeeds.findings`: when no Java is stated and a pom went unread, `java.version` stays
  unknown, and the highest JDK the distribution packages stands beside it, marked as standing in for what the unread
  parent asks. The unknown Java, the empty Maven version and the stand-in stand at the line of the first parent that
  went unread, with a note naming it and saying that building once on the person's machine fills the local repository.
- **Settings that do not parse.** `SettingsReader` turns a parse failure into an `UnknownValue` at that file, "it does
  not parse at line L, column C, and Maven stops on it too; it is not read", and `RepositoryHosts` carries on without
  it. The parser's message is not passed on, since it quotes the file. Size and DTD refusals stay as they are.
- **The forms of a credential.** `SettingsReader` reads, for each server, which fields it holds and the form of each
  secret, without keeping its text: plain, `${env.NAME}` (the variable's name is kept), another expression, Maven 3's
  `{…}` or Maven 4's `{[…]…}`. A header's name is kept, never its value. The credential's finding names the kind (user
  and password, header `<name>`, private key) and what the person stores: the value, the variable's value, or for an
  encrypted secret the plain value, since Porelo does not decrypt. A private key is said to be unsupported by the
  broker, if Porelo's contract library says so.
- **The mirror's reach.** `detect` answers what a mirror takes beside its host, a rule finding `mirror.<id>` with its
  `<mirrorOf>` as written, at its line. The contract's `Repository` carries what a repository mirrors, and `configure`
  writes it as `<mirrorOf>`, so a mirror of `*` reaches the broker for every repository and the profile is not needed.
  The `<mirrorOf>` is answered with the whitespace around its commas removed; one holding any character but
  `[A-Za-z0-9_.:*!,-]` is answered unknown at its line. Beside `credential.<id>` a rule finding `credential-kind.<id>`
  says `basic`, `header:<Name>` or `unsupported`, for the broker to read. Both are contract changes with Porelo's
  contract library and are built only once it has agreed them.
- **The image's Maven.** Where the person's settings mirror every repository, `resolve` fetches Maven through that
  mirror at the same path, with its digest from the same mirror.
- **Red first**, in `MavenEcosystemTest`, `SettingsReaderTest` and the real-Maven comparison: an unread parent answers a
  JDK package, where today it answers none; settings that do not parse warn, where today the checkout is refused; a
  `{…}`, a `{[…]…}`, a `${env.NAME}` and a header token are each named in their form, with a test that no invented
  secret's text appears in any finding; a mirror of `*` makes `configure` write a mirror of `*`.

## Acceptance

- On the fixture without the parent in the local repository, the draft names a JDK, and an image built from it runs
  `mvn --version`; the unknown values stand at the parent's line. With the parent there, the answer is unchanged.
- Real Maven, run with the settings `configure` writes for an invented mirror of every repository against a local
  stand-in for the manager, resolves a parent only that mirror holds and an artifact of Maven Central, and reaches no
  other host.
- For invented servers in each form, the draft names the credential and the person's action; a search of every answer
  and log for the invented secrets finds none.
- Settings with an unquoted attribute value answer a warning at that file and the rest of the checkout.

## To be checked

- That the mirror of `*` alone, without the profile, resolves the parent and Central's artifacts, by real Maven.
- How Maven 4 reads `settings-security4.xml` and writes its encrypted form, against a real Maven 4.
- Whether Porelo's image build reaches the vendors' hosts on a network that reaches only the mirror, and where the
  stand-in JDK's package comes from there.
- Where a first start's note on an imported BOM it could not read comes from: the plugin answers no finding for an
  import, since its first phase stops before imports.
- That Porelo's draft shows the stand-in JDK as something for the person to confirm, as it shows any unknown value.
