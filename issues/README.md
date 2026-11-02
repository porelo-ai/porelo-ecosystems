# Issues

The open tasks of `porelo-ecosystems`, grouped by state; the order within a group is the priority. The rules for an
issue, and who sets which state, are in [AGENTS.md](../AGENTS.md). Its prefix is `EC`. A group with nothing in it is left
out.

## Implementing

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec01"></a>[EC01](EC01-A-Spike-Of-The-Model-Builder-In-A-Native-Image.md) | S | - | Whether MIMA serves the Maven plugin as a native image, by measurement. |

## Backlog

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec02"></a>[EC02](EC02-Maven-Repository-Hosts-Recognized-From-A-Checkout-And-The-Settings.md) | M | [EC01](README.md#ec01) | Given a checkout and the person's Maven settings, the Maven module answers which repository hosts a build must reach, each with its file and line and whose it is: the person's own settings, or the checkout's, which anybody who can push wrote. |

## Accepted

| # | Size | Blocked by | What it covers |
|---|---|---|---|
| <a id="ec03"></a>[EC03](EC03-The-Maven-Plugin-Behind-The-Contract.md) | - | the contract in Porelo, [EC01](README.md#ec01) and [EC02](README.md#ec02) | The Maven plugin as a native executable in its own package, answering Porelo over its contract with the tools and rules a checkout needs. |
| <a id="ec04"></a>[EC04](EC04-The-Gradle-Plugin.md) | - | the contract in Porelo | A checkout built with Gradle gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network, every value with its file and line. |
| <a id="ec05"></a>[EC05](EC05-The-npm-Plugin.md) | - | the contract in Porelo | A checkout built with npm gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network, every value with its file and line. |
| <a id="ec06"></a>[EC06](EC06-The-pip-Plugin.md) | - | the contract in Porelo | A checkout built with pip gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network, every value with its file and line. |
| <a id="ec07"></a>[EC07](EC07-The-Flutter-Plugin.md) | - | the contract in Porelo | A checkout built with Flutter gets its tools and rules recognized by a plugin of its own, behind the `Ecosystem1` contract, under this repository's rules: never running the project's code, no network, every value with its file and line. |
