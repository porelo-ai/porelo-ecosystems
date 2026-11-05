# Decisions

The choices the build-tool plugins are built on, each with why it holds.

| Subject | What holds | Link |
|---|---|---|
| A plugin has nothing to secure | it runs no code and stays bounded, its answer is untrusted input, and the person's confirmation is the control; so it aims at the hosts the real build would reach, each with its file and line, no password appears, and a broken or huge pom gets an answer, not a crash | [entry](#a-plugin-has-nothing-to-secure) |
| An adapter is judged by real builds | open source projects, by a plain clone at pinned commits, must build in a real task from the file Porelo's own first start writes, with only the adapter's code fixed | [entry](#an-adapter-is-judged-by-real-builds) |
| The Maven answer follows real Maven | where Maven's behavior is not obvious, the answer does what real Maven was measured doing; what stays out of it is named | [entry](#the-maven-answer-follows-real-maven) |

## A plugin has nothing to secure

A plugin may run on a checkout just cloned, before the person has said anything. It runs no code of the project and stays bounded, by its limits on what it reads and by Porelo's limit on each call. Porelo treats its answer as untrusted input, and nothing in that answer takes effect until the person has read it and confirmed it: the confirmation is the control, not the plugin. A plugin's task is to make the developer's start easier by reading what the build would use.

So the aim is the right answer: the same hosts the developer's real build would reach, each with its file and line. A finding against a plugin is a wrong answer, never a security finding, and no hostile checkout is hardened against.

What a build reads is input to the answer as it is to the build, and is evaluated where that is simple; what cannot be followed stays an unknown naming it.

A value comes from the builder and names the line of its `<url>`; its origin says where that line stands, not who could steer it.

The real build runs in the box, so what the box decides is an unknown naming it: `${env.X}`, a system property, and a profile on a JDK, an OS or the environment. A `-D` from `.mvn/maven.config` and a file in the checkout are evaluated.

Three things stay: no code of the project runs, no password appears in an answer, and a broken or huge pom gets an answer rather than a crash.

The decision is about what a plugin answers. It says nothing of the agent's box and its egress.

## An adapter is judged by real builds

Unit tests show that an adapter reads what it was written to read. Whether its answer is enough shows only when the tool's real build runs in a real task from that answer, so every adapter is tested this way, each with its own list of projects.

- **The projects:** about ten open source ones of the tool, small libraries, large ones, an application, one using the tool's extension mechanism and one with a package source of its own. Each comes to disk by a plain `git clone` of its public address, at a pinned commit, and nothing else is set up.
- **A reference first:** the project's build without Porelo, at that commit. A project broken by itself, or needing a container engine or a browser, is not counted against the adapter. The reference also fills the account's caches, as a person's own earlier build would; the empty case runs once per project and is reported apart.
- **The file comes from Porelo's real first start:** the question and the yes on the daemon, with the adapter's code answering. No script writes it and nobody edits it.
- **A task with the stub agent:** the tool's real build runs by `podman exec`, in the same network and firewall as an agent. The environment of `podman exec` is compared first with the agent session's, and the build runs the way the session has it. Without tests as the first stage, with tests after.
- **Failures are sorted by the firewall's log:** a refused host, a wrong tool version or a missing tool is the adapter's. A test needing a service is not.
- **Only the adapter's code is fixed,** each fault with a test seen to fail first, and the build runs again from a newly written file. No handwork, no edited file, no workaround, and no rule for one project or by a project's name: only what the repository says.
- **About a third of the projects are held back** and run only at the end, their result stated apart.
- **"Not recognizable" is a result:** what a reader cannot know offline, such as a host a test asks for at run time, gets no fix and is written down.
- **A real agent comes last,** on about two projects, with a cheap model and the words "build the project". It stays out of the corpus, since an agent may work around what should fail.

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
