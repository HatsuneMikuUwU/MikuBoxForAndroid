package main

import (
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"
)

func TestMixedInboundAppliesNativeRouting(t *testing.T) {
	origin := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "mihomo-route-ok")
	}))
	defer origin.Close()
	reservation, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := reservation.Addr().(*net.TCPAddr).Port
	reservation.Close()
	proxy, _ := url.Parse(fmt.Sprintf("http://127.0.0.1:%d", port))
	for _, blocked := range []bool{false, true} {
		rule := "MATCH,DIRECT"
		if blocked {
			rule = "AND,((OR,((DOMAIN-SUFFIX,unused.test),(IP-CIDR,127.0.0.0/8))),(NETWORK,tcp)),REJECT\n  - MATCH,DIRECT"
		}
		cfg := fmt.Sprintf("mixed-port: %d\nmode: rule\nrules:\n  - %s\n", port, rule)
		if _, err := start(cfg, t.TempDir(), -1, "", ""); err != nil {
			t.Fatal(err)
		}
		transport := &http.Transport{Proxy: http.ProxyURL(proxy)}
		client := &http.Client{Transport: transport, Timeout: 3 * time.Second}
		response, err := client.Get(origin.URL)
		if blocked {
			if err == nil && response.StatusCode == http.StatusOK {
				t.Error("REJECT rule allowed traffic")
			}
		} else {
			if err != nil {
				t.Fatal(err)
			}
			body, _ := io.ReadAll(response.Body)
			if string(body) != "mihomo-route-ok" {
				t.Fatalf("unexpected response: %q", body)
			}
		}
		if response != nil {
			response.Body.Close()
		}
		transport.CloseIdleConnections()
		shutdownCore()
	}
}
