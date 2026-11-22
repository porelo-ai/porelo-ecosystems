# Shared

What every ecosystem adapter answers alike, in `org.fuin.porelo.ecosystem.shared`: a value's place with its file, line and origin, the finding the contract takes, the unknown and the unplaced value, and a tool installed in its exact version - the JDK and GraalVM from their vendors, the image lines that fetch, check and unpack it, and the one HTTP client that asks a vendor. It reads no build tool's files: what a pom or a `package.json` says stays in its adapter.

The native executable and the `.deb` and `.rpm` of an adapter are configured once in [the adapters' parent](../pom.xml); an adapter sets its name, main class, its package's words and license, and keeps its control and copyright files under `src/deb`.

It must never depend on a library that reaches a network on its own or decrypts a password: SnakeYAML for what a vendor answers, and the JDK's HTTP client alone.
