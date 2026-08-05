module example.com/spice-goland-concealment

go 1.26.0

toolchain go1.26.5

tool (
	github.com/spice-framework/toolchain/cmd/spice
	github.com/spice-framework/toolchain/cmd/spice-annotation-core
)

require (
	github.com/spice-framework/spice v0.0.0-20260805222830-a2ecd56df246
	github.com/spice-framework/toolchain v0.0.0-20260805222344-fd87027fc195
)

replace github.com/spice-framework/spice => __SPICE_CORE_ROOT__

replace github.com/spice-framework/toolchain => __SPICE_TOOLCHAIN_ROOT__
