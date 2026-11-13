# EC28 — A Workflow's Settings File Is Found Where Its Step Runs

```json
{
  "$schema": "issue-1.json",
  "id": "EC28",
  "title": "A Workflow's Settings File Is Found Where Its Step Runs",
  "state": "Review",
  "size": "S",
  "priority": "Medium",
  "rank": 1,
  "blockedBy": [],
  "covers": "What the reading of the shell form left over: a step's working directory, a folded block, a heredoc, a block of another key, a substitution too deep, and the notes on links and the limit of files."
}
```

**Why this size.** The same two classes of the Maven adapter and their tests; no contract change.

**What must be true.** The settings file the adapter answers for a workflow is the file Maven reads when the step
runs, and a line the adapter does not read as a command is one the shell does not run either.

## Plan

- **Where the step runs.** A step's `working-directory:` and a `cd sub &&` before Maven resolve the file against that
  directory, not the checkout's root; today `cd sub && mvn -s settings.xml` answers the root's `settings.xml`.
- **YAML blocks.** A folded `>`/`>-` block joins its lines without a `\`; the lines of a block under another key
  (`description: |`) and the body of a heredoc are not read as commands.
- **Depth.** A `sh -c` or substitution nested past the limit gives a note naming the workflow instead of passing
  silently.
- **Notes.** A directory named like a workflow (`x.yml/`) is not called "outside the checkout, by a link"; a workflow
  linked from outside counts against the 64 read; the notes come in the order of the files.
- **Level:** unit tests in `MavenEcosystemTest.OwnSettings`. **Red first:** `cd sub && mvn -s settings.xml` with
  `sub/settings.xml` and a root `settings.xml` answers the root's file today.

## Acceptance

- Each case above has a test that failed before and passes after.

## As built

- **`OwnSettings`** reads a workflow as YAML (SnakeYAML's node tree, no objects built) and only `jobs.*.steps[*].run`
  as shell, so a folded block is joined and another key's block is never a command. A step runs in its own
  `working-directory`, else the job's default, else the workflow's - each replacing the other and relative to the
  checkout's root, as GitHub reads them; one that holds `${{ }}` or `$`, or leaves the checkout, is not known, and no
  file is taken there. A script an alias names again is read once. A literal block keeps its lines; any other value
  is placed at its first line. YAML that does not parse is named in a note.
- **`ShellCommand`** keeps where the commands run: a `cd`, `pushd` or `popd` moves the next commands only where it
  surely runs in the shell itself - not after `&&` or `||`, under `if` or a loop, in a pipeline or the background,
  except `cd x || exit 1` - and a subshell, `$(…)`, backticks and `sh -c` leave it as it was; otherwise the directory
  is not known. A line's commands are all read, a cd after the first `-s` included, and a substitution is read before
  its command. Heredocs are read by the parser (`<<`, `<<-`, a quoted or escaped delimiter, not `<<<` or `<<` in
  quotes) and their lines left out. A command nested too deep gives a note. `mvnd` is Maven too.
- **Workflows** are files and links only, never a directory named like one; a link counts against the 64 read; the
  notes come in the order of the files.
- **The reading** (another model) found six reds - a subshell's `cd` leaking, `popd` not read, a `cd` after the first
  `-s` of a line never applied, substitutions read after the line's last `cd`, defaults joined to each other instead
  of replacing them, a conditional `cd` taken as sure - and a scalar alias read once per use; each fixed with a test
  seen red first, as were the folded and quoted line numbers and the heredoc forms it named. Two of its test gaps
  did not hold: the helper writes the root's `settings.xml` for every workflow test. What else it found is
  [EC29](README.md#ec29).
