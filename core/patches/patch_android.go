//go:build android && cmfa

package dns

import (
	"github.com/metacubex/mihomo/component/resolver"
	"sync"
)

var systemResolver []dnsClient
var systemResolverMutex sync.RWMutex

func FlushCacheWithDefaultResolver() {
	resolver.ClearCache()
	resolver.ResetConnection()
}

func UpdateSystemDNS(addr []string) {

	ns := make([]NameServer, 0, len(addr))
	for _, d := range addr {
		ns = append(ns, NameServer{Addr: d, Net: "udp"})
	}

	clients := transform(ns, nil)
	systemResolverMutex.Lock()
	systemResolver = clients
	systemResolverMutex.Unlock()
}

func (c *systemClient) getDnsClients() ([]dnsClient, error) {
	systemResolverMutex.RLock()
	defer systemResolverMutex.RUnlock()
	return append([]dnsClient(nil), systemResolver...), nil
}

func (c *systemClient) ResetConnection() {
	clients, _ := c.getDnsClients()
	for _, r := range clients {
		r.ResetConnection()
	}
}
