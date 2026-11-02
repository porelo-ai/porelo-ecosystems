# Maven

Recognizes what a Maven checkout needs, from its poms and the person's Maven settings, offline and without running any of its code: for now which repository hosts a build must reach, parents and properties merged by Maven's own model builder. It is a library and no plugin yet; it names nothing of Porelo's contract.

It must never depend on anything that reaches a network or decrypts a password: `maven-model-builder` alone, with a resolver that reads only the local repository.
