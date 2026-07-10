package libcore

import (
	"context"
	"os"
	"sync/atomic"

	mDNS "github.com/miekg/dns"
	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	sbdns "github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/experimental/libbox"
	"github.com/sagernet/sing-box/option"
)

// LocalDNSTransport re-exports libbox.LocalDNSTransport for convenience.
type LocalDNSTransport = libbox.LocalDNSTransport

// ExchangeContext re-exports libbox.ExchangeContext for convenience.
type ExchangeContext = libbox.ExchangeContext

// Func re-exports libbox.Func for convenience.
type Func = libbox.Func

// rawQueryFunc performs a raw DNS exchange through android_res_nsend.
// Set by dns_android.go's init on Android 10+; nil elsewhere.
var rawQueryFunc func(networkHandle int64, request []byte) ([]byte, error)

var networkHandle atomic.Int64

// SetNetworkHandle publishes the underlying (non-VPN) network so raw DNS
// queries bypass the tunnel. Pass 0 for the system default network.
func SetNetworkHandle(handle int64) {
	networkHandle.Store(handle)
}

// gLocalDNSTransport supplies DNS for ECH config lookups in http.go.
var gLocalDNSTransport adapter.DNSTransport = nil

var _ adapter.DNSTransport = (*androidLocalTransport)(nil)

// androidLocalTransport resolves through Android's own resolver.
//
// The main service gets its local transport from libbox, which wraps the
// Kotlin LocalDNSTransport. That wrapper reads unexported fields of
// libbox.ExchangeContext, so it cannot be reused here; the standalone test box
// and the ECH lookup path use this transport instead.
type androidLocalTransport struct {
	sbdns.TransportAdapter
}

func newAndroidLocalTransport(tag string, options option.LocalDNSServerOptions) *androidLocalTransport {
	return &androidLocalTransport{
		TransportAdapter: sbdns.NewTransportAdapterWithLocalOptions(C.DNSTypeLocal, tag, options),
	}
}

func (t *androidLocalTransport) Start(stage adapter.StartStage) error { return nil }

func (t *androidLocalTransport) Close() error { return nil }

func (t *androidLocalTransport) Reset() {}

func (t *androidLocalTransport) Exchange(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	if rawQueryFunc == nil {
		return nil, os.ErrInvalid
	}
	request, err := message.Pack()
	if err != nil {
		return nil, err
	}
	responseBytes, err := rawQueryFunc(networkHandle.Load(), request)
	if err != nil {
		return nil, err
	}
	var response mDNS.Msg
	if err = response.Unpack(responseBytes); err != nil {
		return nil, err
	}
	return &response, nil
}
