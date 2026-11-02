# Decisions

The choices the build-tool plugins are built on, each with why it holds.

| Subject | What holds | Link |
|---|---|---|
| A plugin has nothing to secure | it aims at the hosts the real build would reach, each with its file and line; no project code runs, no password appears, and a broken or huge pom gets an answer, not a crash | [entry](#a-plugin-has-nothing-to-secure) |

## A plugin has nothing to secure

Porelo runs on the developer's machine. A setting in the settings or the project that is a problem is one without Porelo too, and Porelo makes it no worse. A plugin's task is to make the developer's start easier by reading what the build would use.

So the aim is the right answer: the same hosts the developer's real build would reach, each with its file and line. A finding against a plugin is a wrong answer, never a security finding, and no hostile checkout is hardened against.

What a build reads is input to the answer as it is to the build, and is evaluated where that is simple; what cannot be followed stays an unknown naming it.

A value comes from the builder and names the line of its `<url>`; its origin says where that line stands, not who could steer it.

The real build runs in the box, so what the box decides is an unknown naming it: `${env.X}`, a system property, and a profile on a JDK, an OS or the environment. A `-D` from `.mvn/maven.config` and a file in the checkout are evaluated.

Three things stay: no code of the project runs, no password appears in an answer, and a broken or huge pom gets an answer rather than a crash.

The decision is about what a plugin answers. It says nothing of the agent's box and its egress.
