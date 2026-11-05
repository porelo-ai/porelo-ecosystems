# Issues

The open tasks of `porelo-ecosystems`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). Its prefix is `EC`. A group with nothing in it is left
out.

## Review

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec01"></a>[EC01](EC01-A-Spike-Of-The-Model-Builder-In-A-Native-Image.md) | S | - | Whether MIMA serves the Maven plugin as a native image, by measurement: it does, and the bare model builder is proposed instead. |
| <a id="ec03"></a>[EC03](EC03-The-Maven-Plugin-Behind-The-Contract.md) | M | - | The Maven plugin as a native executable in its own package, answering Porelo over its contract with the tools and rules a checkout needs. |

## Implementing

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec08"></a>[EC08](EC08-The-Maven-Plugin-Published-As-A-Recommended-Package.md) | M | - | The Maven plugin published as a package of its own that Porelo's package recommends: installed by default, and it can be left out or removed. |

## Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec04"></a>[EC04](EC04-The-Gradle-Plugin.md) | - | - | A checkout built with Gradle gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec05"></a>[EC05](EC05-The-npm-Plugin.md) | - | - | A checkout built with npm gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec06"></a>[EC06](EC06-The-pip-Plugin.md) | - | - | A checkout built with pip gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |
| <a id="ec07"></a>[EC07](EC07-The-Flutter-Plugin.md) | - | - | A checkout built with Flutter gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network while it reads a checkout, every value with its file and line. |

## Idea

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec09"></a>[EC09](EC09-A-Projects-Tools-Installed-In-The-Exact-Version.md) | M | - | What a project asks is installed in the exact version, found once when its file is made, as ordinary image lines fetching by URL and digest, with its paths set and GraalVM brought for a native build. |

## Finished

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec02"></a>EC02 | M | - | Maven Repository Hosts Recognized From A Checkout And The Settings: the Maven module answers the repository hosts a real build would reach, compared with real Maven, each with its file, line and origin. |
