# SlimJar runtime

Source: [CrazyDev05/slimjar, tag 2.1.9](https://github.com/CrazyDev05/slimjar/tree/2.1.9).
The runtime sources come from Maven Central's `de.crazydev22.slimjar:runtime:2.1.9:sources` archive, SHA-256 `7c58c493814d3273c0efa4923a11b2394aa715266ba56ac9c5da94952529a42e`.
`upstream-sources.json` records the original hashes of all 125 Java files; `upstream-changes.txt` lists the files modified locally.
The loader-agent source comes from the same tag and is compiled into `modules/loader-agent.isolated-jar`.
Keep `BuildConstants.VERSION` at `2.1.9` and `BuildConstants.ASM` at `9.8`: these select upstream bootstrap dependencies, independently of the ByteCompanions version.

This internal ByteCompanions module retains SlimJar's API and MIT license. It sets a 10-second connection timeout and a 30-second read timeout before network I/O, publishes completed downloads atomically, rejects failed post-download verification, and retains exception causes. Timeouts apply to individual blocking network operations; they do not impose a total bootstrap deadline or bound DNS lookup or a stream that continues making progress.

ByteCompanions depends on this local Gradle project, excludes the upstream runtime from its retained platform helper, and relocates `io.github.slimjar` into its private library namespace. The module is not published and is not part of VolmLib. Its tests and provenance remain with the source port.
