# porelo-ecosystems

<img src="doc/images/crazy-chicken.svg" width="350" alt="Crazy chicken - just hatched">

> **Crazy chicken - just hatched.** Interfaces, formats and even the purpose may change any day. Use it only to try ideas, never to build on.

Build-tool plugins for Porelo. A plugin reads a checkout's files and the person's own tool settings, offline and without
running any of the project's code, and says what its tasks need: the tools, and the rules such as the hosts a build
must reach. Every value names the file and line it came from; what it cannot place, it names as unknown.

- [plugins](plugins/README.md) - one module per build tool. Maven comes first.

## Building

```
./mvnw -B -s settings.xml verify
```

JDK 25. See [AGENTS.md](AGENTS.md) for the conventions.

## License

GPL-3.0-or-later, see [LICENSE](LICENSE).
