package main

import (
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/metacubex/mihomo/listener"
)

func TestDeferredTunDoesNotCaptureTrafficWhileProvidersLoad(t *testing.T) {
	requested, release := make(chan struct{}), make(chan struct{})
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		close(requested)
		<-release
		fmt.Fprint(w, "proxies: [{name: local, type: direct}]\n")
	}))
	defer server.Close()
	reservation, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := reservation.Addr().(*net.TCPAddr).Port
	reservation.Close()
	config := fmt.Sprintf(`mixed-port: %d
dns: {enable: false}
proxy-providers:
  slow: {type: http, url: '%s', path: ./slow.yaml}
proxy-groups:
  - {name: QA, type: select, use: [slow]}
rules: ['MATCH,DIRECT']
`, port, server.URL)
	done := make(chan error, 1)
	home := t.TempDir()
	go func() { _, err := start(config, home, deferredTunFD, "", ""); done <- err }()
	select {
	case <-requested:
	case <-time.After(10 * time.Second):
		close(release)
		t.Fatal("provider was never requested")
	}
	if listener.LastTunConf.Enable {
		t.Error("TUN was enabled before provider initialization finished")
	}
	close(release)
	if err := <-done; err != nil {
		t.Fatal(err)
	}
	core.running = true
	defer MihomoStop()
	if core.pendingTun == nil || !core.pendingTun.Enable {
		t.Fatal("VPN settings were not retained for attachment")
	}
	if listener.LastTunConf.Enable {
		t.Fatal("prepare unexpectedly opened a TUN")
	}
	if core.effective["dns_enable"] != true {
		t.Fatal("VPN preparation lost its forced DNS configuration")
	}
	if MihomoAttachTun(-1) == 0 {
		t.Fatal("invalid descriptor was accepted")
	}
	if core.running || core.pendingTun != nil {
		t.Fatal("failed attachment did not roll back the prepared core")
	}
	connection, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), time.Second)
	if err == nil {
		connection.Close()
		t.Fatal("failed attachment left mixed proxy listening")
	}
}
