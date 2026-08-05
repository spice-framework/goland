# Dependency review

## Isolation

The GoLand adapter is an independently versioned IntelliJ Platform plugin.
None of its Java, Gradle, test, or IDE dependencies enters Spice's Go compiler,
runtime, generated applications, vendor tree, or deployed services.

## Direct tooling and test dependencies

| Dependency | Pin | Scope | License and maintenance |
| --- | --- | --- | --- |
| IntelliJ Platform Gradle Plugin | 2.18.1 | Build/package | JetBrains-maintained, Apache-2.0 |
| GoLand platform | 2026.2.0.1 / `262.8665.336` | Compile/test/verifier | JetBrains product SDK; not redistributed by the plugin archive |
| Go plugin | bundled `262.8665.336` | Compile/runtime contract | JetBrains-bundled GoLand/IDEA dependency |
| Gradle wrapper | 9.6.1 | Build orchestration | Apache-2.0; distribution and wrapper JAR SHA-256 pinned |
| IntelliJ Plugin Verifier | 1.409 | Binary/API verification | JetBrains-maintained, Apache-2.0 |
| JUnit | 4.13.2 | Test only | Eclipse Public License 1.0 |

`gradle.lockfile` fixes the transitive graph for the downloadable GoLand
distribution used by CI. `gradle-installed-goland.lockfile` independently fixes
the installed-IDE graph because the IntelliJ Platform plugin exposes that same
build through a different `localIde` coordinate. Both lock states are strict.
The plugin archive contains Spice classes, resources, and the Apache-2.0
license; it does not bundle GoLand, the Go plugin, Gradle, Plugin Verifier,
JUnit, a Java runtime, or a second Spice compiler.

## Security, cancellation, and data

The runtime adapter performs no network requests, credential reads, telemetry,
package scanning, or dependency downloads. It starts only the explicitly
configured or inherited `spice` executable with the `lsp` argument and UTF-8
stdio. Shell interpretation is not used. GoLand owns process shutdown and LSP
request cancellation; Spice owns protocol bounds and compiler cancellation.

The bundled annotation reference is read through a fixed classpath resource
with a 2 MiB bound. Virtual declarations are read-only and project-scoped.
Syntax coloring and folding inspect existing comments and maintain no global
mutable application model. Annotation values, source, and secrets are never
sent to an independent telemetry or logging channel.

## Acceptance

Java compilation enables all lint categories and treats warnings as errors.
Repository verification covers real GoLand fixtures, light and dark visual
rendering, archive/configuration validation, and Plugin Verifier against
`GO-262.8665.336`. Installed-plugin acceptance on Windows and Linux exercises
physical-source preservation, Run, Debug, and a real package breakpoint.
Platform updates require an explicit compatibility/lock change and fresh visual
plus binary evidence before release.
