# GoLand plugin release trust contract

The release boundary packages the already verified native plugin; it does not
resolve application modules, install editor dependencies, or introduce a Spice
artifact registry. Gradle remains the plugin build system and the small
`release-tools` Go module uses only Go 1.26.5's standard library to normalize,
inventory, authenticate, and verify its output.

For declared plugin version `0.2.0`, tag `v0.2.0` publishes exactly:

| Asset | Contract |
|---|---|
| `spice-goland_0.2.0.zip` | Installable plugin ZIP with sorted entries and commit-derived timestamps, including recursively normalized JAR metadata |
| `spice-goland_0.2.0_sbom.spdx.json` | SPDX 2.3 inventory bound to the exact package digest |
| `spice-goland_0.2.0_provenance.intoto.jsonl` | in-toto Statement v1 / SLSA provenance binding repository, immutable tag, commit, epoch, and artifact digest |
| `checksums.txt` | LF-terminated, filename-sorted SHA-256 records for the preceding three assets |
| `checksums.txt.sig` | Raw Ed25519 signature over the exact checksum bytes |
| `checksums.txt.pem` | Canonical public key; it must be byte-identical to the committed trust anchor |

The tag must exactly match `pluginVersion` in `compatibility.properties`, point
to a full commit reachable from `origin/main`, and remain immutable. Version
zero releases are deliberately published as GitHub prereleases while the
editor contract evolves.

## Approval and credential boundaries

`release-signing` and `release-publish` accept only `v*` tags and each require a
maintainer approval. The signing environment alone holds
`SPICE_EDITOR_RELEASE_SIGNING_KEY`; the secret is a base64 PKCS#8 Ed25519 key
and is never written to the checkout or uploaded. The publisher has no signing
secret. Repository workflow permissions default to none, all preparation and
verification jobs are read-only, and only the final protected publisher has
`contents: write`.

This is currently a one-maintainer organization, so GitHub's
`prevent_self_review` control is intentionally false: otherwise no release can
complete. The approval is still a deliberate pause for inspecting completed
jobs, but it is not represented as independent human review. Add a second
maintainer and enable self-review prevention before claiming two-person
control.

Two active tag rulesets complement the environments. `Release tag creation
authority` permits only the designated maintainer to create `v*` tags.
`Immutable release tags` rejects every update and deletion with no bypass.
Repository settings are external state and must be audited before each cut;
committed tests fail closed if workflow authority drifts.

## Pipeline

1. An uncredentialed Ubuntu job validates the tag and exact compatibility
   inputs, runs `verifyRepository`, packages the verified ZIP, and uploads an
   unsigned candidate.
2. An uncredentialed Windows job rebuilds and packages the same commit.
3. After signing approval, the candidate's canonical checksums are signed only
   if its structure, SBOM, and provenance validate against source metadata and
   the private key matches the committed anchor.
4. A separate no-secret job authenticates all six assets from the committed
   anchor and requires the Linux and Windows unsigned assets to be
   byte-identical.
5. After publish approval, the sole write-capable job reverifies, creates a
   draft prerelease, uploads, downloads, byte-compares, reverifies, and only
   then publishes it.

No job downloads a signing tool or trusts the public key shipped beside a
release. Actions are pinned by full commit. Artifact handoffs are scoped to one
workflow run.

## Consumer verification

Download all six assets and check them from a trusted checkout of the matching
tag:

```text
go -C release-tools run ./cmd/editor-release verify \
  -root .. -input /path/to/downloads \
  -version v0.2.0 -commit <full-tag-commit> -epoch <commit-unix-time>
```

The verifier rejects extra/missing files, path traversal, duplicate entries,
noncanonical keys/checksums, a mismatched trust anchor, invalid signatures,
wrong versions/commits/epochs, stale provenance or SBOM data, and malformed
plugin packages.

Key rotation is an explicit reviewed release change: generate a fresh Ed25519
key offline, update the protected signing environment and committed anchor in
one bounded change, record the new DER SHA-256 fingerprint, pass
`verifyRepository`, and cut no tag until hosted main is green. Never reuse a
key from another Spice repository.
