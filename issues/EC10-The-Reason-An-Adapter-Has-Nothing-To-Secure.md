# EC10 — The Reason An Adapter Has Nothing To Secure

```json
{
  "$schema": "issue-1.json",
  "id": "EC10",
  "title": "The Reason An Adapter Has Nothing To Secure",
  "state": "Review",
  "size": "XS",
  "priority": "Medium",
  "rank": 4,
  "blockedBy": [],
  "covers": "The rule that an adapter has nothing to secure says its real reason: the adapter runs no code and stays bounded, its answer is untrusted input, and the person's confirmation is the control."
}
```

**Why this size.** the wording of one rule and of the decision behind it; no code.

**What must be true.** Whoever reads the rule that an adapter has nothing to secure finds the reason that holds when an adapter runs on a checkout just cloned, before the person has said anything: the adapter runs no code and stays bounded, its answer is untrusted input, and the person's confirmation is the control.

## Plan

- `AGENTS.md`, this repository's own part, and `doc/decisions.md`, its index row and its entry, state that reason in place of the old one, that a setting which is a problem is one without Porelo too.
- What follows from it stays: the aim is the right answer, no hostile checkout is hardened against, and the entry's title and anchor stay as they are.
- No code changes, so no test goes red first.

## Acceptance

- Both places give the same reason, in those three parts, and neither still rests on the developer's machine as the reason.
- The document checks are green.
