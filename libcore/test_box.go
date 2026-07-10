package libcore

import (
	"context"
	"errors"
	"net"
	"net/http"
	"sync"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	sbdns "github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json"
	M "github.com/sagernet/sing/common/metadata"

	"libcore/device"
)

// TestBox is a standalone sing-box instance used to measure the latency of a
// single profile. The main service runs under libbox.CommandServer, which only
// exposes an asynchronous URLTest over the outbounds of the *running* config,
// so profile-list latency testing needs its own instance.
type TestBox struct {
	access sync.Mutex
	box    *box.Box
	cancel context.CancelFunc
	closed bool
}

func testBoxContext() context.Context {
	dnsRegistry := include.DNSTransportRegistry()
	if rawQueryFunc != nil {
		sbdns.RegisterTransport[option.LocalDNSServerOptions](dnsRegistry, C.DNSTypeLocal,
			func(ctx context.Context, logger log.ContextLogger, tag string, options option.LocalDNSServerOptions) (adapter.DNSTransport, error) {
				return newAndroidLocalTransport(tag, options), nil
			})
	}
	return box.Context(
		context.Background(),
		include.InboundRegistry(),
		include.OutboundRegistry(),
		include.EndpointRegistry(),
		dnsRegistry,
		include.ServiceRegistry(),
		include.CertificateProviderRegistry(),
	)
}

func NewTestBox(config string) (b *TestBox, err error) {
	defer device.DeferPanicToError("NewTestBox", func(err_ error) { err = err_ })

	ctx, cancel := context.WithCancel(testBoxContext())

	options, err := json.UnmarshalExtendedContext[option.Options](ctx, []byte(config))
	if err != nil {
		cancel()
		return nil, err
	}

	instance, err := box.New(box.Options{Options: options, Context: ctx})
	if err != nil {
		cancel()
		return nil, err
	}

	return &TestBox{box: instance, cancel: cancel}, nil
}

func (b *TestBox) Start() (err error) {
	defer device.DeferPanicToError("TestBox.Start", func(err_ error) { err = err_ })
	b.access.Lock()
	defer b.access.Unlock()
	if b.closed {
		return errors.New("closed")
	}
	return b.box.Start()
}

func (b *TestBox) Close() (err error) {
	defer device.DeferPanicToError("TestBox.Close", func(err_ error) { err = err_ })
	b.access.Lock()
	defer b.access.Unlock()
	if b.closed {
		return nil
	}
	b.closed = true
	b.cancel()
	return b.box.Close()
}

// createProxyHttpClient dials through instance's default outbound, or directly
// when instance is nil. Replaces boxapi.CreateProxyHttpClient.
func createProxyHttpClient(instance *box.Box) *http.Client {
	return &http.Client{
		Transport: &http.Transport{
			DisableKeepAlives: true,
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				if instance == nil {
					var dialer net.Dialer
					return dialer.DialContext(ctx, network, addr)
				}
				outbound := instance.Outbound().Default()
				if outbound == nil {
					return nil, errors.New("no default outbound")
				}
				return outbound.DialContext(ctx, network, M.ParseSocksaddr(addr))
			},
		},
	}
}

// measureRTT reports the round trip time of a single request, replacing
// libneko/speedtest.UrlTest with UrlTestStandard_RTT.
func measureRTT(client *http.Client, link string, timeout int32) (int32, error) {
	if timeout <= 0 {
		timeout = 3000
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Millisecond)
	defer cancel()
	defer client.CloseIdleConnections()

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, link, nil)
	if err != nil {
		return 0, err
	}

	start := time.Now()
	response, err := client.Do(request)
	if err != nil {
		return 0, err
	}
	response.Body.Close()

	latency := time.Since(start).Milliseconds()
	if latency < 1 {
		latency = 1
	}
	return int32(latency), nil
}

// UrlTest measures latency through instance, or directly when instance is nil.
func UrlTest(instance *TestBox, link string, timeout int32) (latency int32, err error) {
	defer device.DeferPanicToError("UrlTest", func(err_ error) { err = err_ })
	if instance == nil {
		return measureRTT(createProxyHttpClient(nil), link, timeout)
	}
	return measureRTT(createProxyHttpClient(instance.box), link, timeout)
}

// UrlTestMain measures latency through the running CommandServer service.
func UrlTestMain(link string, timeout int32) (latency int32, err error) {
	defer device.DeferPanicToError("UrlTestMain", func(err_ error) { err = err_ })
	instance := runningBox()
	if instance == nil {
		return 0, errors.New("core not started")
	}
	return measureRTT(createProxyHttpClient(instance), link, timeout)
}
