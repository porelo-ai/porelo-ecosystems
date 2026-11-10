# EC19 — A Central Moved By A Property Names Its File

```json
{
  "$schema": "issue-1.json",
  "id": "EC19",
  "title": "A Central Moved By A Property Names Its File",
  "state": "Idea",
  "size": "-",
  "priority": "Medium",
  "rank": 2,
  "blockedBy": [],
  "covers": "A Central moved by the property `maven.repo.central` is answered with the file and line that set it, not as the machine's with no file."
}
```

**What must be true.** When a checkout or the person's settings move Maven's Central by the property `maven.repo.central`, the Maven adapter answers that host with the file and line where the property is set and that file's origin, not as a value of the machine with no file.
