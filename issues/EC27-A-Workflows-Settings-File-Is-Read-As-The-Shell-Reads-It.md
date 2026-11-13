# EC27 — A Workflow's Settings File Is Read As The Shell Reads It

```json
{
  "$schema": "issue-1.json",
  "id": "EC27",
  "title": "A Workflow's Settings File Is Read As The Shell Reads It",
  "state": "Implementing",
  "size": "S",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "The checked-in settings file a workflow passes to Maven is found in the forms a shell reads - a continued line, a path to `mvn`, a quoted command, `.YML` - and what is not followed is named, never skipped silently."
}
```

**Why this size.** one class and its tests in the Maven adapter; no contract change.

**What must be true.** When a workflow of the checkout passes a settings file inside it to Maven, the adapter finds it
in the forms a shell reads, and a workflow it does not follow - too large, unreadable, past the limit of files, behind
a link out of the checkout - is named in the answer with its file, so the person sees why a settings file was
generated.

## Plan

- **`OwnSettings`**: join a line ending in `\` with the next before matching; take `mvn` and `mvnw` after a path
  (`/usr/bin/mvn`, `$(pwd)/mvnw`) and inside quotes or backticks; read `*.yml`, `*.yaml` in any case; skip a mention
  that is an argument of another command (`echo mvn -s x`).
- **What is not followed**: a workflow over 1 MiB, one that cannot be read, those past the 64th, and a workflow file
  or directory linked from outside the checkout each give an unplaced note naming the file; the files are not read.
- **Two workflows naming two files**: the first is taken and the other named in a note, rather than the order of
  files deciding unseen.
- **Level:** unit tests in `MavenEcosystemTest.OwnSettings`. **Red first:** a workflow with `mvn -B \` and `-s
  settings.xml` on the next line answers no `ownSettings` today.

## Acceptance

- Each form above answers `extension.ownSettings` with the line that names the file; `echo mvn -s settings.xml` does
  not.
- A workflow too large, past the limit or linked from outside gives a note naming it, and its content is not read.
