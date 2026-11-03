# Decisions

The choices the build-tool plugins are built on, each with why it holds.

| Subject | What holds | Link |
|---|---|---|
| A plugin has nothing to secure | it aims at the hosts the real build would reach, each with its file and line; no project code runs, no password appears, and a broken or huge pom gets an answer, not a crash | [entry](#a-plugin-has-nothing-to-secure) |
| The Maven answer follows real Maven | where Maven's behavior is not obvious, the answer does what real Maven was measured doing; what stays out of it is named | [entry](#the-maven-answer-follows-real-maven) |

## A plugin has nothing to secure

Porelo runs on the developer's machine. A setting in the settings or the project that is a problem is one without Porelo too, and Porelo makes it no worse. A plugin's task is to make the developer's start easier by reading what the build would use.

So the aim is the right answer: the same hosts the developer's real build would reach, each with its file and line. A finding against a plugin is a wrong answer, never a security finding, and no hostile checkout is hardened against.

What a build reads is input to the answer as it is to the build, and is evaluated where that is simple; what cannot be followed stays an unknown naming it.

A value comes from the builder and names the line of its `<url>`; its origin says where that line stands, not who could steer it.

The real build runs in the box, so what the box decides is an unknown naming it: `${env.X}`, a system property, and a profile on a JDK, an OS or the environment. A `-D` from `.mvn/maven.config` and a file in the checkout are evaluated.

Three things stay: no code of the project runs, no password appears in an answer, and a broken or huge pom gets an answer rather than a crash.

The decision is about what a plugin answers. It says nothing of the agent's box and its egress.

## The Maven answer follows real Maven

The answer is compared with `help:effective-pom` from the Maven the wrapper pins, on the same fixtures, in tests only. One run of real Maven takes about 2.7 s, so the comparison costs seconds per fixture; a first run on a clean machine fetches the help plugin, so it runs online. On six of Porelo's own checkouts the answer takes 4 to 303 ms.

What real Maven was measured doing, where a reading suggests otherwise:

- **An imported BOM is not read.** A repository or a property declared in a BOM or its parent does not reach the effective model, and a Java release set there stays the pom's. Two-phase building stopped after the first phase never asks for the import, so a missing BOM answers the same repositories and properties, and only managed versions are lost.
- **A settings profile `activeByDefault` applies beside an explicit one**, does not activate a pom's profile of its id, and does not stop a pom's default one.
- **A parent with an empty `relativePath` comes from the local repository**, even where the checkout's own reactor holds it.
- **`.mvn/maven.config` and `.mvn/jvm.config` each redirect a repository of the person's settings through a property**, since Maven fills in settings when it reads them; `-Dmaven.repo.central` in `jvm.config` moves nothing there.
- **Each line of `maven.config` is one argument:** `-s ci/settings.xml` on one line names a file with a space in its name, and Maven stops; on two lines it reads the file.
- **An empty `<mirrorOfLayouts/>` means any layout**, in both of Maven's selectors, not `default,legacy`.
- **A settings profile has no modules**, so a `<module>` is never named at the settings.

A pom doubling through 40 levels, and 100,000 uses of a 1 MiB value, end in under two seconds with a 1 GiB heap, as a refusal naming the pom, so filling in needs no bound of its own. This is not measured in a native image.

What stays out of the answer, each named here because no fixture shows it:

- **Builder and comparison differ in version:** the module builds on `maven-model-builder` 3.10.0 and compares with the wrapper's Maven 3.9.15, so what only 3.10 does has no reference. It stays because the comparison runs the Maven a build runs; a wrapper on 3.10 closes it.
- **A server is matched by id alone,** while Maven 3.10 can hold a credential back from a host not declared for that server, so the answer can call a server known where such a build sends nothing. It stays because only an id is answered, and never a credential's value; a use that stores a credential per host would change it.
- **A settings profile on a property or a file stays an unknown,** though a pom's is evaluated. It stays because an unknown names it rather than guessing; a checkout that needs it answered would change it.
- **A `-D` or `-P` on the command line is not seen:** it stands in no file, and nothing else can be read for it.
