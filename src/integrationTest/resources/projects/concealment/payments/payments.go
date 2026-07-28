package payments

type Processor interface {
	Process() error
}
