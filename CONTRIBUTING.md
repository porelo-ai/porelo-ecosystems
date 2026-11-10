# Contributing

Changes here follow the project's guide, [Contributing to Porelo](https://porelo-ai.github.io/porelo-project/contributing/):
how a change starts as a GitHub issue, what a commit carries, and what a change must satisfy. The
rules are kept there and in the shared block of [`AGENTS.md`](AGENTS.md), not here.

## In this repository

A change starts as an issue in [`issues/`](issues/README.md), or in
[porelo-project's issues](https://github.com/porelo-ai/porelo-project/blob/main/issues/README.md) while it is not yet
clear which repository builds it.

A change is checked as the Build checks it, with Java 25 and Maven only. The build, the unit tests and the shared checks
(`check-actions`, `check-shared`, `check-citations`, `check-readmes`) run in one call:

    ./mvnw -B -U -s settings.xml verify

The native executable and its package are built and checked with the profile `native`, which needs GraalVM as
`JAVA_HOME`:

    ./mvnw -B -U -s settings.xml -Pnative verify

A change to documents only runs the tests that read documents:

    ./mvnw -B -U -s settings.xml -Pdocuments test

An adapter is judged by real builds with [`corpus/`](corpus/), as [`AGENTS.md`](AGENTS.md) describes.
