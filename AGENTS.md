# Spice GoLand implementation contract

## Mission

Deliver the primary native editor experience for valid-Go Spice annotations.
The plugin owns presentation, native IntelliJ references, completion plumbing,
Run/Debug integration, and installed-IDE interaction evidence. The shared Spice
compiler and LSP remain authoritative for framework semantics.

## Repository boundaries

- Go 1.26.5, Java 25, Gradle 9.6.1, and GoLand 2026.2.0.1 are mandatory.
- Core Spice, the standalone toolchain, and Petclinic are explicit external
  verification inputs. Never infer them from a parent directory or copy their
  source into this repository.
- `compatibility.properties` is the reviewed compatibility tuple and must use
  full Git object IDs.
- The physical editor document must always retain valid `// @...` Go comments.
- Folding and coloring are presentation only and must never mutate PSI, VFS,
  clipboard, temporary runner input, or Git content.
- Run and Debug operate on complete packages and generated files, never a
  `gocommand-*` single-file fragment.
- Native fallback behavior may preserve folding, color, and indexed navigation
  during an LSP restart, but it must not invent compiler or DI semantics.
- Do not download or install Go modules from editor analysis paths.

## Delivery

Work directly on local `main` in bounded commits. Before each commit run:

```text
gradlew verifyRepository
```

using the explicit `SPICE_CORE_ROOT`, `SPICE_TOOLCHAIN_ROOT`, and
`SPICE_PETCLINIC_ROOT` checkouts. Any
change to presentation, navigation, completion edits, Run/Debug, or the LSP
lifecycle must also pass `gradlew verifyInstalledIde` on Windows and Linux.
Commit only green trees, fetch immediately before push, and stop if
`origin/main` moved unexpectedly.

Release tags are a separate protected operation. The repository-owned release
workflow must retain an uncredentialed source/package gate, a `release-signing`
approval boundary with the only signing-secret reference, an independent
Windows rebuild and public-key verifier, and a `release-publish` approval
boundary containing the only `contents: write` permission. Never move or
delete a release tag. The solo-maintainer environments necessarily permit the
same maintainer to approve after inspecting predecessor jobs; they do not
pretend to provide two-person review.
