package main

// @import { Implements, Service } from "github.com/StevenBuglione/spice/annotation/core"

// @Service
// @Implements(payments.Pro)
type Stripe struct{}

// @Service
// @Implements(payments.Processor)
type ManualProcessor struct{}

func (*ManualProcessor) Process() error { return nil }
