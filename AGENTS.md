# Rules for working in this repository

## Shared across the Porelo repositories

> **BEGIN Shared Area** · sha256 `e9f242a328fb9719` · changed 2026-11-21T16:39Z

Identical in every repository `project.yml` names but `porelo-parent`; the markers carry the SHA-256 of the lines
between them and the time they last changed. Change it in the channel first, never in one copy. The same holds for
every shared skill `porelo-*` in `.agents/skills/`, whose markers also cover its frontmatter, and for
`.claude/skills`, a link to `../.agents/skills` so that Claude Code finds them.

### Agents

- **Each repository has one responsible agent; the channel and who owns what are defined in `.AGENTS.md`** or fed
  into the agent's context at start. An agent writes only in its own repositories and asks the owner for the rest;
  the coordinating agent may read the others, never write in them.
- **A contract between repositories changes only after agreement in the channel or by an operator decision**, in
  both repositories.
- **An ambiguous answer is asked about once, plainly, never guessed**; when an answer changes, everything built on
  the old one is looked for.
- **Only `AGENTS.md`, `.AGENTS.md` and the skills survive a session; the harness's memory is never used.** A rule,
  a decision about how to work or an open point goes into one of them at once.
- **No agent opens a dialog or any other prompt that waits for the person at its console, unless that person
  explicitly asked for one there**: a dialog stops the agent's work until the person comes.
- **A question from the operator is answered, nothing more**; an action needs an explicit instruction.
- **To the operator, plain technical terms, never coined words**, in the language the operator writes in;
  Porelo's own names (Porelo-Vault, Vault-Passphrase, gate, task) stay as they are.

### Hard limits

- **A secret never appears in a command line, a log line, a file name or an answer**; it reaches a process
  through its environment or standard input and is stored only encrypted.
- **An agent's commit names its help in `Assisted-by: <tool>, <model>` and never carries `Signed-off-by:` or
  `Co-Authored-By:`**, whatever the harness suggests. Signing and release tags belong to the operator.
- **A refusal by the harness or a sub-agent is reported, never worked around.**
- **Fail closed and loud**: when a dependency is unreachable, a key unknown or a check cannot run, stop non-zero
  and say why. Never silently do less.
- **Porelo protects the person from the agents and from outside, not from their own rights or choices**; every
  guard and every finding is judged by this.

### Claims and writing

- **Measure before you claim, and say what was measured and what inferred**; a change is checked by observing it,
  never by an exit code alone, and a claim of absence needs a complete search that is shown to find a known hit.
- **Every date or time written is read from the system (`date -u`) first.**
- **When a thing is right in two forms, keep one.**
- **What is written states what is true now, in US English**: no dates, no history, nobody named, only roles; it
  says what changed and why, never who asked. A finding worth keeping is committed, never left in a conversation.
- **Every open task is an issue in its repository's GitHub Issues, on the project's board**, never a TODO in code;
  code and anything that ships never cite an issue number.

### Which skill to load

Before any of these, load the skill named, from `.agents/skills/`. A rule shared by several repositories goes
into a shared skill or this block, one that concerns only this machine into `.AGENTS.md`.

| Before you | Load |
|---|---|
| start work inside a Porelo task (`/workspace`, user `agent`) | `porelo-task` |
| start work anywhere else | `porelo-workstation` |
| commit, amend, rebase, hand over a push | `porelo-commits` |
| file an issue or change its state | `porelo-issues` |
| write, run or judge a test | `porelo-testing` |
| run anything on a VM or rented machine | `porelo-machines` |
| write an operator test guide or prepare a walk | `porelo-operator-guide` |
| hand over, or build an issue of size M or larger | `porelo-review` |
| change code, a pom, a workflow or a release | `porelo-code` |
| write documentation or a decision; hand over | `porelo-writing` |
| start analysis or implementation; hand over a push | `porelo-estimates` |

> **END Shared Area** · sha256 `e9f242a328fb9719`

## What this repository is

The ecosystem adapters of Porelo, one Maven module per build tool: Maven first, then Gradle, npm, pip and Flutter. An
adapter is a native executable of its own, found by Porelo at run time and spoken to over a varlink contract; Porelo
names no build tool. What an adapter answers becomes a tools draft and a rules draft for the person to read.

- **An adapter never runs the project's code**: no build tool, no wrapper, no build script, no Maven core extension, no
  plugin of the project's. It reads files and the models built from them.
- **An adapter has nothing to secure, because it is not the control.** It runs no code of the project and stays bounded,
  also on a checkout just cloned; its answer is untrusted input to Porelo, and the person's confirmation is the control.
  So it aims at the right answer: the hosts the real build would reach. What the build reads is input to the answer; no
  hostile checkout is hardened against, and a finding against an adapter is a wrong answer, never a security finding.
- **An adapter reaches no network while it reads a checkout or the person's settings.** What the build would fetch is
  read from the person's local caches only; a value that sits in something not found there is named as unknown, with
  where it is set, never guessed.
- **The one online step is when `project.yml` is made:** an adapter asks the vendors' sources once for the newest stable
  version of what the project asks, visibly in the draft; offline or on a vendor's failure, the draft says unknown and
  names what was asked.
- **An adapter only bootstraps.** Once `project.yml` exists, what stands in it is the person's, and an adapter does not
  read it against anything.
- **A broken or huge file gets an answer, not a crash**: limits on size, XML without DTDs or fetching.
- **Every value names its file and line, and where that stands**: the person's own files, the checkout, or the machine.
  A credential is named by its id only, and its value appears nowhere.
- **Porelo's contract comes from Core**, in `porelo`'s `ecosystems/api`. Logic that does not need it, such as what a pom
  and the person's settings say about repository hosts, is built and tested without it.
- **Under `GPL-3.0-or-later`, and nothing goes to Maven Central.** An adapter is a native executable in a package of its own, found at run time over the contract; nothing builds against an artifact here.

## Work in this repository

- **Issues are in the repository's GitHub Issues**, each named by its reference there
  (`porelo-ai/porelo-ecosystems#<number>`); one carried over from the Markdown files keeps its old id (`EC`) as a fixed
  alias.
- **An issue covers one build tool only**, so one never waits for another.
- **A module holds no code until its issue is built**: it stays a `pom` with a `README.md` until then.
- **`corpus/` judges an adapter by real builds:** on an account kept for it, with Porelo running, `corpus.sh <tool>.conf <name> <url> <version> [commit]`, the arguments as a line of `<tool>.projects` names them, clones one project, builds it without Porelo as the reference, then by `podman exec` in the session's environment in a real task made from Porelo's real first start. Results go under `~/corpus/results`, and are written by hand into the issue that ran them. A new build tool brings its own `.conf`.

## Skills

`java-code-review`, `test-quality`, `security-audit`, `clean-code`, `solid-principles`, `graal`.
