# Issues

The open tasks of `porelo-ecosystems`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). Its prefix is `EC`. A group with nothing in it is left
out.

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec19"></a>[EC19](EC19-A-Central-Moved-By-A-Property-Names-Its-File.md) | - | - | A Central moved by the property `maven.repo.central` is answered with the file and line that set it, not as the machine's with no file. |
| <a id="ec15"></a>[EC15](EC15-What-The-Exact-Version-Step-Left-Unread.md) | - | - | What installing a project's tools in their exact version left unmeasured: a replaced `image.run`, the HTTP client's size, GraalVM's next asset names, Python's digests, and the online step's time. |

## Analyzed

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec25"></a>[EC25](EC25-The-Maven-Adapter-Matches-Porelos-Own-Builds.md) | S | - | Run on each of Porelo's own Maven repositories with its `porelo.yml` set aside, the Maven adapter's tools answer installs the same tools as that file, or names what it left out: `shfmt` for Spotless, `rpm` and `dpkg-dev` for the packaging plugins, GraalVM for a native build. |
| <a id="ec24"></a>[EC24](EC24-The-Adapters-Share-Their-Answer-And-Packaging.md) | M | - | What every adapter needs and the Maven adapter holds alone today - the answer with file, line and origin, the unknown value, the exact-version download, the native build and the package - moves to `adapters/shared` and the parent pom, and the Build publishes a package per adapter, so a second adapter is only its own reading. |
| <a id="ec04"></a>[EC04](EC04-The-Gradle-Adapter.md) | L | [EC24](README.md#ec24) | A checkout built with Gradle gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec05"></a>[EC05](EC05-The-npm-Adapter.md) | M | [EC24](README.md#ec24) | A checkout built with npm gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec06"></a>[EC06](EC06-The-pip-Adapter.md) | M | [EC24](README.md#ec24) | A checkout built with pip gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec07"></a>[EC07](EC07-The-Flutter-Adapter.md) | M | [EC24](README.md#ec24) | A checkout built with Flutter gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |

## Estimated

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec18"></a>[EC18](EC18-The-Tests-Follow-The-Test-Pyramid.md) | M | - | The tests follow the test pyramid: every merge case of poms and settings asserted in a unit test, the comparison with real Maven moved out of the unit stage so it runs in under a minute, and the install lines a tool gets in the image run in a test. |

## Backlog

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec29"></a>[EC29](EC29-A-Workflows-Settings-File-Is-Found-In-Rarer-Forms.md) | S | - | What the reading of the step's directory left over: forms where the adapter now takes no file though the build names one, and a line number inside a folded block. |

## Review

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec30"></a>[EC30](EC30-The-Docs-Say-What-The-Code-Does.md) | S | - | Every sentence of the user-facing documents - the READMEs, doc/ and the unreleased changelog entries - is borne out by the code on main; a sentence the code contradicts is fixed, and code that is wrong is filed as a bug of its own. |
| <a id="ec01"></a>[EC01](EC01-A-Spike-Of-The-Model-Builder-In-A-Native-Image.md) | S | - | Whether MIMA serves the Maven adapter as a native image, by measurement: it does, and the bare model builder is proposed instead. |
| <a id="ec28"></a>[EC28](EC28-A-Workflows-Settings-File-Is-Found-Where-Its-Step-Runs.md) | S | - | What the reading of the shell form left over: a step's working directory, a folded block, a heredoc, a block of another key, a substitution too deep, and the notes on links and the limit of files. |
| <a id="ec08"></a>[EC08](EC08-The-Maven-Adapter-Published-As-A-Recommended-Package.md) | M | - | The Maven adapter published as a package of its own that Porelo's package recommends: installed by default, and it can be left out or removed. |
| <a id="ec27"></a>[EC27](EC27-A-Workflows-Settings-File-Is-Read-As-The-Shell-Reads-It.md) | S | - | The checked-in settings file a workflow passes to Maven is found in the forms a shell reads - a continued line, a path to `mvn`, a quoted command, `.YML` - and what is not followed is named, never skipped silently. |
| <a id="ec03"></a>[EC03](EC03-The-Maven-Adapter-Behind-The-Contract.md) | M | - | The Maven adapter as a native executable in its own package, answering Porelo over its contract with the tools and rules a checkout needs. |
| <a id="ec10"></a>[EC10](EC10-The-Reason-An-Adapter-Has-Nothing-To-Secure.md) | XS | - | The rule that an adapter has nothing to secure says its real reason: the adapter runs no code and stays bounded, its answer is untrusted input, and the person's confirmation is the control. |
| <a id="ec16"></a>[EC16](EC16-A-Project-Behind-An-In-House-Repository-Manager-Builds-In-A-Task.md) | M | - | A multi-module Maven project behind an in-house repository manager builds in a task: a JDK stands in when its parent went unread, a mirror that takes every repository reaches the task's settings, a credential is named in its form without its value, and settings that do not parse are warned of. |
| <a id="ec17"></a>[EC17](EC17-The-Maven-Adapter-Says-Its-Version.md) | S | - | The Maven adapter answers with the version it was built as when Porelo asks it to describe itself, taken from its build, so a person can tell which build answered. |
| <a id="ec12"></a>[EC12](EC12-A-Default-Host-Says-It-Is-Maven-s-Own.md) | XS | - | A host that comes from no file, such as Maven's built-in Central, says in its note that it is Maven's built-in default. |
| <a id="ec13"></a>[EC13](EC13-The-Maven-Adapter-Judged-By-Real-Builds.md) | L | - | The Maven adapter judged by whether a real build runs in a real task, on a corpus of open source projects, with each fault fixed in the Java code alone. |
| <a id="ec14"></a>[EC14](EC14-A-Toolchain-Plugin-Is-Warned-Of.md) | S | - | A plugin that picks or fetches its own JDK, `toolchains-maven-plugin` or Maven's own, gets a warning at its line naming the JDK it asks, instead of a build stopping on a refused host or a missing toolchain. |
| <a id="ec20"></a>[EC20](EC20-The-Notes-Name-The-File-Porelo-Writes.md) | XS | - | The notes that say what to write name `porelo.yml`, the file Porelo now writes, not `project.yml`. |
| <a id="ec11"></a>[EC11](EC11-The-Word-Ecosystem-Adapter.md) | M | - | "Ecosystem adapter" replaces "plugin" for what Porelo finds at run time, before a first release fixes the word: `plugins/` becomes `adapters/`, with every text a person reads; the package and installed paths stay. |

## Finished

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec22"></a>EC22 | S | - | The Build Takes Its Packages From A Cache: the Ubuntu packages that read the built packages come from a cache keyed by their pinned versions, and every cache of the workflows is dropped by GitHub once no run has read it for seven days. |
| <a id="ec21"></a>EC21 | S | - | The Maven Adapter Ships Its Third-Party Notices: the deb and the rpm carry one `THIRD-PARTY-NOTICES` file with the full license and notice texts of every library and the runtime compiled into the executable, from the shared generator, named in the deb's copyright file and marked as a license in the rpm. |
| <a id="ec23"></a>EC23 | XS | - | The Issue Heads Are Checked Every Day: the daily *Shared rules check* and each pull request's run check every issue head against issue-1.json with the released porelo-release's `issues check issues`. |
| <a id="ec26"></a>EC26 | S | - | A Checked-In Settings File Is The Task's Alone: when the checkout holds the Maven settings its build uses, `Configure` writes no `.m2/settings.xml` for the task and the draft names the checked-in file; only without one does the generated file stand in. |
| <a id="ec09"></a>EC09 | L | - | A Project's Tools Installed In The Exact Version: what a project asks is installed in the exact version its vendor names, found once when its file is made, as ordinary image lines fetching by URL and digest, with its paths set and GraalVM brought for a native build. |
| <a id="ec02"></a>EC02 | M | - | Maven Repository Hosts Recognized From A Checkout And The Settings: the Maven module answers the repository hosts a real build would reach, compared with real Maven, each with its file, line and origin. |
