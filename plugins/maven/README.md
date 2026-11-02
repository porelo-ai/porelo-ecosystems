# Maven

The Maven plugin of Porelo, a native executable found at run time over the contract. It recognizes what a Maven checkout needs, from its poms and the person's Maven settings, offline and without running any of its code: the JDK, Maven and system packages, the repository hosts a build must reach, parents and properties merged as Maven merges them, its memory and the ids of its credentials, each with its file and line. For a task it writes the settings that reach its repositories through the broker.

`./mvnw -s settings.xml -Pnative verify` builds the executable and its package, and tests it.

It must never depend on anything that reaches a network or decrypts a password: `maven-model-builder` alone, with a resolver that reads only the local repository.
