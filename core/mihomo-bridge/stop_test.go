package main

import (
	"fmt"
	"net"
	"testing"
	"time"
)

func TestStopClosesMixedTCPAndUDPListeners(t *testing.T) {
	reservation, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := reservation.Addr().(*net.TCPAddr).Port
	reservation.Close()
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	cfg := fmt.Sprintf("mixed-port: %d\nmode: rule\nrules:\n  - MATCH,DIRECT\n", port)
	for cycle := 0; cycle < 2; cycle++ {
		if _, err := start(cfg, t.TempDir(), -1, "", ""); err != nil {
			t.Fatal(err)
		}
		core.running = true
		conn, err := net.DialTimeout("tcp", addr, time.Second)
		if err != nil {
			t.Fatalf("listener did not start: %v", err)
		}
		conn.Close()
		MihomoStop()
		conn, err = net.DialTimeout("tcp", addr, time.Second)
		if err == nil {
			conn.Close()
			t.Fatal("TCP proxy still accepts connections after stop")
		}
		udp, err := net.ListenPacket("udp", addr)
		if err != nil {
			t.Fatalf("UDP proxy port still occupied after stop: %v", err)
		}
		udp.Close()
		MihomoStop() // stopping twice must remain safe
	}
}
