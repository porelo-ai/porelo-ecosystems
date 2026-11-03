# EC09 — A Project's Tools Installed In The Exact Version

**Status:** Idea.

**What must be true.** What a project asks is installed in the exact version: the JDK, Maven and any other recognized tool, so that a distribution's packages no longer decide it and a pom asking Maven 3.9 no longer meets 3.8.7.

- A tool manager such as SDKMAN is used only while building: a build stage installs and fetches, and the final image holds only the artifact and its paths, with no `sdk`, no `sdkman-init` and no installer.
- The paths are set correctly: `JAVA_HOME`, `JDK_HOME`, `JRE_HOME`, `MAVEN_HOME`, `PATH`, and for GraalVM `GRAALVM_HOME`.
- `native-maven-plugin` is recognized and brings GraalVM with `native-image` and every library it needs, so such a build works without a hand-written snippet.
- A person signs exact versions: what they read in `project.yml` is what gets built.
- `mvnw` keeps priority for Maven.

## To be checked

By a spike on a real image and a real build, red or green per point:

1. A stage `FROM base AS tools` with SDKMAN, then `COPY --from=tools` of Temurin 25, Maven 3.9 and GraalVM into a final image: `sdk` is absent, and the paths work in a non-login shell and for the `agent` user.
2. What the final image lacks that a distribution's package would have brought: a JDK's and `native-image`'s runtime libraries, `ca-certificates`.
3. A Hello World and a native build in the box, with no network at run time beyond Maven.
4. Who picks the exact identifier, as the plugin is offline: an online step at `project init`, a table in the plugin, or something better.
5. Trust and repeatability, read in SDKMAN's code: the installer's pin or checksum, archive checksums, whether old versions stay available; against fetching from the vendor by URL and sha256 without SDKMAN.
6. Whether the Gradle, npm, pip and Flutter plugins fit this shape, by reading.
7. The form in `project.yml` and the contract's new answer beside `package.<name>`, with Porelo itself naming no build tool.
