# Spice for GoLand

This repository owns the primary native GoLand integration for the
[Spice Framework](https://github.com/spice-framework/spice). It preserves valid
Go source while making declaration comments feel like native annotations:

- exact zero-width concealment of canonical `// ` prefixes;
- theme-aware annotation and import syntax colors;
- modifier-hover underlining, navigation, Go to Implementation, rich
  documentation, parameter information, and completion;
- shared compiler/LSP diagnostics and safe, version-checked edits;
- complete-package Spice Run and native Go/Delve Debug behavior;
- an actionable offline-safe plugin health view.

The plugin never makes naked `@` source valid, performs dependency injection,
or replaces GoLand's Go type system. Framework meaning comes from the exact
Spice compiler selected by the application; editor presentation remains useful
while that compiler restarts.

## Compatibility

[`compatibility.properties`](compatibility.properties) is the reviewed tuple
for the plugin artifact, GoLand build, Java/Go/Gradle toolchains, core Spice,
and the standalone Petclinic acceptance application. The core and Petclinic
Git object IDs are test inputs, not runtime dependencies of the installed
plugin. The complete maintenance, license, security, cancellation, and data
review is recorded in [`docs/dependency-review.md`](docs/dependency-review.md).

## Build and verify

Provide canonical core and Petclinic checkouts explicitly:

```text
SPICE_CORE_ROOT=/path/to/spice \
SPICE_PETCLINIC_ROOT=/path/to/petclinic \
./gradlew --no-daemon --no-parallel --console=plain verifyRepository
```

PowerShell:

```powershell
$env:SPICE_CORE_ROOT = "D:\src\spice"
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

## Source boundary

The Java package name and plugin ID remain stable for upgrade compatibility.
This repository owns only the IntelliJ Platform adapter. Compiler, SDK, CLI,
and application source belong to their independently versioned repositories.
