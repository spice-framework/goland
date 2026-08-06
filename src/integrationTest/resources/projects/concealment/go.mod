module example.com/spice-goland-concealment

go 1.26.0

toolchain go1.26.5

tool (
	github.com/spice-framework/toolchain/cmd/spice
	github.com/spice-framework/toolchain/cmd/spice-annotation-core
)

require (
	github.com/spice-framework/spice v0.0.0-20260806030852-fde9cc3f18e2
	github.com/spice-framework/toolchain v0.0.0-20260806030852-b348e03d419d
)
