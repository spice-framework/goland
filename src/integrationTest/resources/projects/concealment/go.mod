module example.com/spice-goland-concealment

go 1.26.0

toolchain go1.26.5

tool (
	github.com/spice-framework/toolchain/cmd/spice
	github.com/spice-framework/toolchain/cmd/spice-annotation-core
)

require (
	github.com/spice-framework/spice v0.1.0-preview.1.0.20260807050649-46ba4660cfb0
	github.com/spice-framework/toolchain v0.1.0-preview.1.0.20260807044408-6598abca8196
)
