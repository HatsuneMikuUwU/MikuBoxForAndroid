//go:build with_gvisor

package main

import (
	"testing"

	"github.com/metacubex/gvisor/pkg/tcpip/stack"
	tun "github.com/metacubex/sing-tun"
)

type recordingLinkEndpoint struct {
	stack.LinkEndpoint
	dispatcher stack.NetworkDispatcher
}

func (e *recordingLinkEndpoint) Attach(dispatcher stack.NetworkDispatcher) {
	e.dispatcher = dispatcher
}

func TestGVisorFilterPreservesDetach(t *testing.T) {
	endpoint := &recordingLinkEndpoint{}
	filter := &tun.LinkEndpointFilter{LinkEndpoint: endpoint}
	// Only attachment identity is exercised; no packet is delivered here.
	dispatcher := &struct{ stack.NetworkDispatcher }{}
	filter.Attach(dispatcher)
	if endpoint.dispatcher == nil || endpoint.dispatcher == dispatcher {
		t.Fatal("live dispatcher must be wrapped by the packet filter")
	}
	filter.Attach(nil)
	if endpoint.dispatcher != nil {
		t.Fatal("detach was wrapped: gVisor pollers will keep the closed TUN alive")
	}
}
