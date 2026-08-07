# Spice for GoLand

Unified documentation: [spiceframework.dev/tools/goland](https://spiceframework.dev/tools/goland/).

This repository owns the primary native GoLand integration for the
[Spice Framework](https://github.com/spice-framework/spice). It preserves valid
Go source while making declaration comments feel like native annotations:

- exact zero-width concealment of canonical `// ` prefixes;
- theme-aware annotation and import syntax colors;
- modifier-hover underlining, navigation, Go to Implementation, rich
  documentation, parameter information, and completion;
- a class-oriented structure view that nests constructors, static factories,
  fields, interface methods, and receiver methods beneath the owning type;
- native Spice intentions for constructors, method co-location, function-to-
  method/component conversion, explicit `@Implements`, interfaces,
  implementations, and configuration-owned `@Bean` methods;
- shared compiler/LSP diagnostics and safe, version-checked edits;
- complete-package Spice Run and native Go/Delve Debug behavior resolved from
  explicit named, aliased, or namespace `@import` bindings;
- an actionable offline-safe plugin health view.

The plugin never makes naked `@` source valid, performs dependency injection,
or replaces GoLand's Go type system. Framework meaning comes from the exact
Spice compiler selected by the application; editor presentation remains useful
while that compiler restarts.

## Class-oriented authoring

The Structure tool window presents valid Go through a type-centric projection.
`NewOrderService` appears as the constructor of `OrderService`; `ParseType`,
`MustType`, and `TypeFrom...` functions appear as static factories. Selecting
any projected member navigates to its ordinary Go declaration, and the source
file itself is never rewritten for presentation.

At an applicable declaration, **Alt+Enter** exposes branded `Spice:` actions
for all class-oriented edits. Generated managed types include explicit
constructors, cross-file moves preserve policy comments and referenced Go
imports, and interface/implementation generation uses deterministic filenames.
The edits remain ordinary, gofmt-compatible Go and preserve physical
`// @...` annotation comments.

## Compatibility

[`compatibility.properties`](compatibility.properties) is the reviewed tuple
for the plugin artifact, GoLand build, Java/Go/Gradle toolchains, core Spice,
the standalone Spice toolchain, and the Petclinic acceptance application. The
three Git object IDs are test inputs, not runtime dependencies of the installed
plugin. Descriptor source/navigation remains rooted in core; CLI/LSP build,
Run, and Debug fixtures use the standalone toolchain. The complete maintenance,
license, security, cancellation, and data
review is recorded in [`docs/dependency-review.md`](docs/dependency-review.md).

The current public module pair is core
`v0.1.0-preview.1.0.20260807050649-46ba4660cfb0` and toolchain
`v0.1.0-preview.1.0.20260807044408-6598abca8196`. The installed-IDE fixture declares those
versions and replaces them only with the corresponding compatibility checkouts
for deterministic, offline UI verification. Those test-only replacements are
never written to an application or shipped as plugin configuration.

## Build and verify

Provide canonical core, toolchain, and Petclinic checkouts explicitly:

```text
SPICE_CORE_ROOT=/path/to/spice \
SPICE_TOOLCHAIN_ROOT=/path/to/toolchain \
SPICE_PETCLINIC_ROOT=/path/to/petclinic \
./gradlew --no-daemon --no-parallel --console=plain verifyRepository
```

PowerShell:

```powershell
$env:SPICE_CORE_ROOT = "D:\src\spice"
$env:SPICE_TOOLCHAIN_ROOT = "D:\src\toolchain"
$env:SPICE_PETCLINIC_ROOT = "D:\src\petclinic"
.\gradlew.bat --no-daemon --no-parallel --console=plain verifyRepository
```

`verifyRepository` validates wrapper integrity and the compatibility inputs,
runs the unit/editor fixture suite, builds the exact plugin ZIP, validates the
plugin structure/project, and runs JetBrains Plugin Verifier.

The decisive installed-plugin gate is:

```text
./gradlew --no-daemon --no-parallel --console=plain verifyInstalledIde
```

On headless Linux, prefix it with `xvfb-run -a`. It launches the packaged ZIP
in the pinned GoLand build, opens the real standalone Petclinic module, proves
physical comment preservation and reclaimed width, exercises light/dark
colors, hover/navigation/documentation/health, runs the complete application,
and reaches a native package breakpoint. A second project proves explicit
interface-binding authoring. Screenshots and interaction evidence are written
below `build/reports/visual`.

The plugin archive is produced below `build/distributions`. Install that ZIP
through **Settings | Plugins | Install Plugin from Disk**. The application must
provide its own compatible `spice` executable; the plugin never downloads one
silently.

## Authenticated releases

Immutable `vMAJOR.MINOR.PATCH` tags build the same verified plugin ZIP on Linux
and Windows, normalize ZIP/JAR metadata, and require byte-identical results.
The release contains the installable ZIP, an SPDX 2.3 SBOM, in-toto/SLSA
provenance, canonical SHA-256 checksums, the emitted Ed25519 public key, and a
detached signature of the exact checksum bytes. Verify against the reviewed
repository anchor at
[`security/release/ed25519-public.pem`](security/release/ed25519-public.pem),
whose DER SHA-256 fingerprint is
`4633e35fe23310edaa766d32c43e5b26303bf9c6a4d1cc433b1ff8e35ec3512f`.

The private key is available only to the protected `release-signing`
environment. A separate no-secret job verifies signature, structure,
provenance, SBOM, checksums, and cross-platform reproducibility before the
protected `release-publish` job receives the workflow's sole write permission.
See [`docs/releasing.md`](docs/releasing.md) for the artifact and operator
contract.

## Source boundary

The Java package name and plugin ID remain stable for upgrade compatibility.
This repository owns only the IntelliJ Platform adapter. Compiler, SDK, CLI,
and application source belong to their independently versioned repositories.
