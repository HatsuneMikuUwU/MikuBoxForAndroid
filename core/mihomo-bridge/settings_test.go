package main

import (
	"bufio"
	"fmt"
	constant "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	listenerconfig "github.com/metacubex/mihomo/listener/config"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"net/url"
	"strconv"
	"testing"
	"time"
)

func TestGlobalTLSVerificationReachesProviderOverrides(t *testing.T) {
	provider := map[string]any{"override": map[string]any{"udp": true}}
	node := map[string]any{"skip-cert-verify": true}
	raw := map[string]any{"proxies": []any{node}, "proxy-providers": map[string]any{"remote": provider}}
	applyTLSVerification(raw, true)
	if node["skip-cert-verify"] != false {
		t.Fatal("node retained insecure TLS")
	}
	override := provider["override"].(map[string]any)
	if override["skip-cert-verify"] != false || override["udp"] != true {
		t.Fatal("provider override lost TLS policy or unrelated options")
	}
}

func TestTLSVerificationControlsRealProxyHandshake(t *testing.T) {
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodConnect {
			t.Error("expected CONNECT")
			return
		}
		conn, rw, err := w.(http.Hijacker).Hijack()
		if err != nil {
			return
		}
		defer conn.Close()
		conn.SetDeadline(time.Now().Add(4 * time.Second))
		fmt.Fprint(rw, "HTTP/1.1 200 Connection Established\r\n\r\n")
		rw.Flush()
		if _, err := http.ReadRequest(bufio.NewReader(rw)); err != nil {
			return
		}
		fmt.Fprint(rw, "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok")
		rw.Flush()
	}))
	defer server.Close()
	_, port, _ := net.SplitHostPort(server.Listener.Addr().String())
	for _, verify := range []bool{false, true} {
		listener, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		inbound := listener.Addr().(*net.TCPAddr).Port
		listener.Close()
		config := fmt.Sprintf("mixed-port: %d\nproxies:\n  - {name: TLS, type: http, server: 127.0.0.1, port: %s, tls: true, skip-cert-verify: true}\nrules: [MATCH,TLS]\n", inbound, port)
		// YAML list requires MATCH,TLS to be one rule.
		config = config[:len(config)-len("rules: [MATCH,TLS]\n")] + "rules:\n  - MATCH,TLS\n"
		if _, err := start(config, t.TempDir(), -1, "", fmt.Sprintf(`{"miku-tls-verify":%t,"tcp-concurrent":true,"unified-delay":true,"find-process-mode":"always","geodata-loader":"memconservative"}`, verify)); err != nil {
			t.Fatal(err)
		}
		general := executor.GetGeneral()
		if !general.TCPConcurrent || !general.UnifiedDelay || general.FindProcessMode.String() != "always" || general.GeodataLoader != "memconservative" {
			t.Fatalf("settings not applied to live core: %+v", general)
		}
		proxy, _ := url.Parse("http://127.0.0.1:" + strconv.Itoa(inbound))
		transport := &http.Transport{Proxy: http.ProxyURL(proxy), DisableKeepAlives: true}
		client := &http.Client{Transport: transport, Timeout: 3 * time.Second}
		response, requestErr := client.Get("http://example.invalid/")
		if !verify && requestErr != nil {
			shutdownCore()
			t.Fatal(requestErr)
		}
		if response != nil {
			body, _ := io.ReadAll(response.Body)
			response.Body.Close()
			if !verify && string(body) != "ok" {
				t.Errorf("TLS proxy did not carry traffic: %q", body)
			}
			if verify && response.StatusCode == http.StatusOK {
				t.Error("untrusted certificate was accepted")
			}
		}
		transport.CloseIdleConnections()
		shutdownCore()
	}
}

func TestProcessLookupUsesOriginalFakeIPSocket(t *testing.T) {
	metadata := &constant.Metadata{DstIP: netip.MustParseAddr("1.1.1.1"), DstPort: 443,
		RawDstAddr: &net.TCPAddr{IP: net.ParseIP("198.18.0.2"), Port: 443}}
	host, port := socketDestination(metadata)
	if host != "198.18.0.2" || port != 443 {
		t.Fatalf("looked up translated socket %s:%d", host, port)
	}
}

func TestDnsFilterOverridePreservesUntouchedProfileFields(t *testing.T) {
	raw := map[string]any{"dns": map[string]any{"fallback-filter": map[string]any{
		"geoip": false, "domain": []any{"+.example.com"},
	}}}
	mergeSection(raw, "dns", map[string]any{"fallback-filter": map[string]any{"geoip-code": "CN"}})
	filter := raw["dns"].(map[string]any)["fallback-filter"].(map[string]any)
	if filter["geoip"] != false || filter["geoip-code"] != "CN" || len(filter["domain"].([]any)) != 1 {
		t.Fatalf("lost profile DNS policy: %#v", filter)
	}
}

// A closed Android fd is routinely reused by the next VPN session. Leaving
// the old config enabled makes Mihomo skip creating that next TUN listener.
func TestShutdownInvalidatesTunConfiguration(t *testing.T) {
	previous := listenerconfig.Tun{Enable: true, Device: "MikuBox", FileDescriptor: 94}
	listener.LastTunConf = previous
	shutdownCore()
	if listener.LastTunConf.Enable || listener.LastTunConf.Equal(previous) {
		t.Fatal("shutdown retained an enabled TUN config; reused fd would skip startup")
	}
}
