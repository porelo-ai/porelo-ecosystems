# Issues

The open tasks of `porelo-ecosystems`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). Its prefix is `EC`. A group with nothing in it is left
out.

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec15"></a>[EC15](EC15-What-The-Exact-Version-Step-Left-Unread.md) | - | - | What installing a project's tools in their exact version left unmeasured: a replaced `image.run`, the HTTP client's size, GraalVM's next asset names, Python's digests, and the online step's time. |
| <a id="ec19"></a>[EC19](EC19-A-Central-Moved-By-A-Property-Names-Its-File.md) | - | - | A Central moved by the property `maven.repo.central` is answered with the file and line that set it, not as the machine's with no file. |

## Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec18"></a>[EC18](EC18-The-Tests-Follow-The-Test-Pyramid.md) | M | - | The tests follow the test pyramid: every merge case of poms and settings asserted in a unit test, the comparison with real Maven moved out of the unit stage so it runs in under a minute, and the install lines a tool gets in the image run in a test. |
| <a id="ec04"></a>[EC04](EC04-The-Gradle-Adapter.md) | - | - | A checkout built with Gradle gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec05"></a>[EC05](EC05-The-npm-Adapter.md) | - | - | A checkout built with npm gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec06"></a>[EC06](EC06-The-pip-Adapter.md) | - | - | A checkout built with pip gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec07"></a>[EC07](EC07-The-Flutter-Adapter.md) | - | - | A checkout built with Flutter gets its tools and rules recognized by an adapter of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |

## Review

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec22"></a>[EC22](EC22-The-Build-Takes-Its-Packages-From-A-Cache.md) | S | - | The Build takes the Ubuntu packages that read the built packages from a cache keyed by their pinned versions, and every cache of the workflows is dropped by GitHub once no run has read it for seven days. |
| <a id="ec01"></a>[EC01](EC01-A-Spike-Of-The-Model-Builder-In-A-Native-Image.md) | S | - | Whether MIMA serves the Maven adapter as a native image, by measurement: it does, and the bare model builder is proposed instead. |
| <a id="ec08"></a>[EC08](EC08-The-Maven-Adapter-Published-As-A-Recommended-Package.md) | M | - | The Maven adapter published as a package of its own that Porelo's package recommends: installed by default, and it can be left out or removed. |
| <a id="ec03"></a>[EC03](EC03-The-Maven-Adapter-Behind-The-Contract.md) | M | - | The Maven adapter as a native executable in its own package, answering Porelo over its contract with the tools and rules a checkout needs. |
| <a id="ec21"></a>[EC21](EC21-The-Maven-Adapter-Ships-Its-Third-Party-Notices.md) | S | - | The Maven adapter's packages carry the full license and notice texts of every library compiled into its native executable, from the shared generator, named in the deb's copyright file and marked as a license in the rpm. |
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
| <a id="ec09"></a>EC09 | L | - | A Project's Tools Installed In The Exact Version: what a project asks is installed in the exact version its vendor names, found once when its file is made, as ordinary image lines fetching by URL and digest, with its paths set and GraalVM brought for a native build. |
| <a id="ec02"></a>EC02 | M | - | Maven Repository Hosts Recognized From A Checkout And The Settings: the Maven module answers the repository hosts a real build would reach, compared with real Maven, each with its file, line and origin. |
