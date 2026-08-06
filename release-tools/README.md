# Editor release tool

This isolated Go 1.26.5 module is the standard-library-only packaging trust
boundary for the GoLand plugin. It provides deterministic `package`, protected
`sign`, and public `verify` commands. It is not linked into the plugin and adds
no runtime or application dependency.

The canonical configuration is [`release.json`](release.json). Production
commands are documented in [`../docs/releasing.md`](../docs/releasing.md) and
enforced by `.github/workflows/release.yml`. The signer accepts key material
only from `SPICE_EDITOR_RELEASE_SIGNING_KEY`; callers must not put that value on
a command line or in a file within the checkout.
