package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"context"
	"encoding/json"
	"errors"
	"net/netip"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/config"
	constant "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/dns"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/listener"
	listenerconfig "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
	tun "github.com/metacubex/sing-tun"
)

var core = struct {
	sync.Mutex
	running    bool
	lastErr    string
	groupOrder []string
	effective  map[string]any
	pendingTun *listenerconfig.Tun
}{}

// bridgeRevision identifies the compiled bridge in exported diagnostics; bump
// it whenever the native side changes so a log proves which build produced it.
const bridgeRevision = "2026-09-28.2"

// Prepare the complete VPN configuration before Android installs catch-all routes.
const deferredTunFD = -2

// MihomoStart initializes the Alpha core in-process. The Android app owns the
// VPN interface and passes its already-open descriptor to Mihomo's TUN inbound.
//
//export MihomoStart
func MihomoStart(configText *C.char, homeDir *C.char, tunFD C.int, dnsOverride *C.char, overridesJson *C.char) C.int {
	core.Lock()
	defer core.Unlock()

	if core.running {
		shutdownCore()
		core.running = false
	}

	order, err := start(C.GoString(configText), C.GoString(homeDir), int(tunFD), C.GoString(dnsOverride), C.GoString(overridesJson))
	if err != nil {
		shutdownCore()
		core.lastErr = err.Error()
		core.groupOrder = nil
		return 1
	}

	core.lastErr = ""
	core.groupOrder = order
	core.running = true
	return 0
}

// MihomoStop closes listeners before Android closes its ParcelFileDescriptor.
//
//export MihomoStop
func MihomoStop() {
	core.Lock()
	defer core.Unlock()
	log.Infoln("[Stop] closing traffic sampler")
	shutdownCore()
	core.running = false
	log.Infoln("[Stop] done")
}

// MihomoAttachTun adopts a descriptor only after providers, DNS and routing are ready.
//
//export MihomoAttachTun
func MihomoAttachTun(fd C.int) C.int {
	core.Lock()
	defer core.Unlock()
	if err := attachTun(int(fd)); err != nil {
		core.lastErr = err.Error()
		shutdownCore()
		core.running = false
		return 1
	}
	return 0
}

func attachTun(fd int) error {
	if !core.running || core.pendingTun == nil || fd < 0 {
		return errors.New("VPN core is not prepared for a TUN descriptor")
	}
	conf := *core.pendingTun
	core.pendingTun = nil
	conf.FileDescriptor = fd
	listener.ReCreateTun(conf, tunnel.Tunnel)
	if !listener.LastTunConf.Enable {
		return errors.New("TUN listener failed to start, see the core log")
	}
	return nil
}

// Upstream Shutdown is intended for process exit and only cleans up TUN.
// An embedded core must also release proxy sockets and live connections.
func shutdownCore() {
	core.pendingTun = nil
	stopTrafficSampler()
	listener.ReCreateHTTP(0, tunnel.Tunnel)
	listener.ReCreateSocks(0, tunnel.Tunnel)
	listener.ReCreateRedir(0, tunnel.Tunnel)
	listener.ReCreateTProxy(0, tunnel.Tunnel)
	listener.ReCreateMixed(0, tunnel.Tunnel)
	listener.ReCreateShadowSocks("", tunnel.Tunnel)
	listener.ReCreateVmess("", tunnel.Tunnel)
	listener.ReCreateTuic(listenerconfig.TuicServer{}, tunnel.Tunnel)
	listener.PatchTunnel(nil, tunnel.Tunnel)
	listener.PatchInboundListeners(nil, tunnel.Tunnel, true)
	dns.ReCreateServer("", nil, nil)
	// Cleanup closes the device but retains LastTunConf. Android may reuse the
	// same fd on reconnect, causing ReCreateTun to skip opening the new device.
	listener.ReCreateTun(listenerconfig.Tun{}, tunnel.Tunnel)
	executor.Shutdown()
	statistic.DefaultManager.Range(func(tracker statistic.Tracker) bool {
		_ = tracker.Close()
		return true
	})
}

// MihomoLastError returns the latest startup/configuration failure.
//
//export MihomoLastError
func MihomoLastError() *C.char {
	core.Lock()
	defer core.Unlock()
	return C.CString(core.lastErr)
}

// MihomoVersion exposes the bundled core version for user-agent and diagnostics.
//
//export MihomoVersion
func MihomoVersion() *C.char {
	return C.CString("v" + constant.Version)
}

// MihomoTraffic exposes the core's traffic accounting without requiring an
// HTTP controller to be enabled in a user configuration. The session fields
// count from the moment this core was started, which is what the app shows for
// the current connection; the totals keep growing for the life of the process.
//
//export MihomoTraffic
func MihomoTraffic() *C.char {
	uplink, downlink := statistic.DefaultManager.Now()
	uploadTotal, downloadTotal := statistic.DefaultManager.Total()
	uploadSession, downloadSession := trafficSession()
	payload, _ := json.Marshal(struct {
		Upload          int64 `json:"upload"`
		Download        int64 `json:"download"`
		UploadTotal     int64 `json:"uploadTotal"`
		DownloadTotal   int64 `json:"downloadTotal"`
		UploadSession   int64 `json:"uploadSession"`
		DownloadSession int64 `json:"downloadSession"`
	}{uplink, downlink, uploadTotal, downloadTotal, uploadSession, downloadSession})
	return C.CString(string(payload))
}

// MihomoTrafficByProxy reports the traffic each node and proxy group carried
// during this session, including the groups a connection was routed through, as
// a JSON object keyed by proxy name. Returns "{}" when the core is stopped.
//
//export MihomoTrafficByProxy
func MihomoTrafficByProxy() *C.char {
	return C.CString(trafficByProxy())
}

// MihomoConnections mirrors GET /connections: every connection the core is
// currently carrying, with its source, destination, matched rule, proxy chain
// and byte counters. The app shows this as a live list without having to open a
// controller port. Returns "{}" when the core is stopped.
//
//export MihomoConnections
func MihomoConnections() *C.char {
	payload, err := json.Marshal(statistic.DefaultManager.Snapshot())
	if err != nil {
		return C.CString("{}")
	}
	return C.CString(string(payload))
}

// MihomoCloseConnections drops every live connection, mirroring
// DELETE /connections. Returns 0 on success.
//
//export MihomoCloseConnections
func MihomoCloseConnections() C.int {
	statistic.DefaultManager.Range(func(tracker statistic.Tracker) bool {
		_ = tracker.Close()
		return true
	})
	return 0
}

// MihomoCloseConnection drops one connection by its id, mirroring
// DELETE /connections/{id}. Returns 0 when the connection was found.
//
//export MihomoCloseConnection
func MihomoCloseConnection(id *C.char) C.int {
	tracker := statistic.DefaultManager.Get(C.GoString(id))
	if tracker == nil {
		return 1
	}
	if err := tracker.Close(); err != nil {
		return 1
	}
	return 0
}

// MihomoProxies mirrors the RESTful controller's GET /proxies, returning every
// proxy and group as JSON so the app can render a node list without opening a
// controller port. Returns {"proxies": {...}} or an empty string on failure.
//
//export MihomoProxies
func MihomoProxies() *C.char {
	payload, err := json.Marshal(map[string]any{
		"proxies": tunnel.Proxies(),
	})
	if err != nil {
		return C.CString("")
	}
	return C.CString(string(payload))
}

// MihomoSelectProxy points a selector group at one of its members, mirroring
// PUT /proxies/{group}. An empty name clears a pinned node on auto groups
// (url-test/fallback), returning them to automatic selection. Returns 0 on
// success, 1 on failure (see MihomoLastError).
//
//export MihomoSelectProxy
func MihomoSelectProxy(groupName *C.char, proxyName *C.char) C.int {
	core.Lock()
	defer core.Unlock()

	group := C.GoString(groupName)
	name := C.GoString(proxyName)

	proxy, exist := tunnel.Proxies()[group]
	if !exist {
		core.lastErr = "unknown proxy group: " + group
		return 1
	}
	selector, ok := proxy.Adapter().(outboundgroup.SelectAble)
	if !ok {
		core.lastErr = group + " is not a selector"
		return 1
	}
	if name == "" {
		selector.ForceSet("")
		cachefile.Cache().SetSelected(group, "")
		core.lastErr = ""
		return 0
	}
	if err := selector.Set(name); err != nil {
		core.lastErr = err.Error()
		return 1
	}
	cachefile.Cache().SetSelected(group, name)
	core.lastErr = ""
	return 0
}

// MihomoGroupOrder returns the proxy-group names in the order the running
// configuration declared them, as a JSON array. The REST proxies map is
// alphabetical, so the app uses this to render groups like the config author
// intended. Returns "[]" before the first start or for group-less configs.
//
//export MihomoGroupOrder
func MihomoGroupOrder() *C.char {
	core.Lock()
	defer core.Unlock()
	order, _ := json.Marshal(core.groupOrder)
	return C.CString(string(order))
}

// MihomoSetMode switches the routing mode of the running core without a
// restart, mirroring the controller's PATCH /configs. "global" sends every
// connection to the GLOBAL selector (its members are all nodes and all proxy
// groups, so an exit can be a node or a whole group), "direct" stops proxying
// altogether and "rule" restores rule matching. Returns 0 on success, 1 on
// failure.
//
// It deliberately takes no core lock: the app switches modes from a tap, and a
// tap that lands while the core is still starting must not wait for it. A mode
// set while the core is stopped is harmlessly overwritten by the next start,
// which applies the configuration's own mode.
//
//export MihomoSetMode
func MihomoSetMode(mode *C.char) C.int {
	var parsed tunnel.TunnelMode
	if err := parsed.UnmarshalText([]byte(strings.TrimSpace(C.GoString(mode)))); err != nil {
		log.Warnln("[Mode] rejected: %s", err.Error())
		return 1
	}
	tunnel.SetMode(parsed)
	return 0
}

// MihomoRuntimeInfo reports the effective core configuration and build facts
// (stack, MTU, DNS mode, whether gVisor is compiled in, last error) without
// exposing node credentials, so a log export can prove how the core was
// actually configured. Returns a JSON object.
//
//export MihomoRuntimeInfo
func MihomoRuntimeInfo() *C.char {
	core.Lock()
	defer core.Unlock()
	info := map[string]any{
		"bridge_revision": bridgeRevision,
		"with_gvisor":     tun.WithGVisor,
		"running":         core.running,
		"last_error":      core.lastErr,
		// The live routing mode: the app's mode switch has to show what the core
		// is actually doing, not only what the profile asked for.
		"mode": tunnel.Mode().String(),
	}
	for key, value := range core.effective {
		info[key] = value
	}
	payload, _ := json.Marshal(info)
	return C.CString(string(payload))
}

// MihomoRules mirrors GET /rules, exposing the parsed rule list (type, payload,
// target policy) for a read-only routing viewer. Returns "[]" when stopped.
//
//export MihomoRules
func MihomoRules() *C.char {
	type ruleInfo struct {
		Type    string `json:"type"`
		Payload string `json:"payload"`
		Target  string `json:"target"`
	}
	rawRules := tunnel.Rules()
	rules := make([]ruleInfo, 0, len(rawRules))
	for _, raw := range rawRules {
		rules = append(rules, ruleInfo{
			Type:    raw.RuleType().String(),
			Payload: raw.Payload(),
			Target:  raw.Adapter(),
		})
	}
	payload, _ := json.Marshal(rules)
	return C.CString(string(payload))
}

// MihomoProxyDelay URL-tests a single proxy, mirroring GET /proxies/{name}/delay.
// Returns {"delay": ms} on success or {"error": "..."} on failure/timeout.
//
//export MihomoProxyDelay
func MihomoProxyDelay(proxyName *C.char, testURL *C.char, timeoutMS C.int) *C.char {
	name := C.GoString(proxyName)
	url := C.GoString(testURL)

	proxy, exist := tunnel.Proxies()[name]
	if !exist {
		return C.CString(`{"error":"unknown proxy"}`)
	}

	expectedStatus, _ := utils.NewUnsignedRanges[uint16]("")
	ctx, cancel := context.WithTimeout(context.Background(), time.Millisecond*time.Duration(int(timeoutMS)))
	defer cancel()

	delay, err := proxy.URLTest(ctx, url, expectedStatus)
	if err != nil || delay == 0 {
		return C.CString(`{"error":"timeout"}`)
	}
	payload, _ := json.Marshal(map[string]any{"delay": delay})
	return C.CString(string(payload))
}

//export MihomoProxyEndpoint
func MihomoProxyEndpoint(proxyName *C.char) *C.char {
	proxy := tunnel.Proxies()[C.GoString(proxyName)]
	for i := 0; proxy != nil && i < 32; i++ {
		next := proxy.Unwrap(&constant.Metadata{}, false)
		if next == nil {
			payload, _ := json.Marshal(map[string]any{"address": proxy.Addr(), "type": proxy.Type().String()})
			return C.CString(string(payload))
		}
		proxy = next
	}
	return C.CString(`{}`)
}

// MihomoValidateDns checks that a DNS override block is well-formed YAML that
// unmarshals to a mapping. Returns an empty string when valid (or blank), or a
// human-readable error otherwise, so the editor can reject bad input up front.
//
//export MihomoValidateDns
func MihomoValidateDns(dnsYaml *C.char) *C.char {
	text := strings.TrimSpace(C.GoString(dnsYaml))
	if text == "" {
		return C.CString("")
	}
	dns := map[string]any{}
	if err := yaml.Unmarshal([]byte(text), &dns); err != nil {
		return C.CString(err.Error())
	}
	return C.CString("")
}

func start(configText, homeDir string, tunFD int, dnsOverride, overridesJson string) ([]string, error) {
	core.pendingTun = nil
	vpnMode := tunFD >= 0 || tunFD == deferredTunFD
	constant.SetHomeDir(homeDir)
	// Path.Config() is a bare relative name that the mihomo CLI resolves
	// against its own working directory. Android app processes run with a
	// read-only "/" as cwd, so the initial config.yaml write would fail;
	// resolve it to an absolute path the same way the CLI does.
	constant.SetConfig(filepath.Join(homeDir, "config.yaml"))
	if err := config.Init(homeDir); err != nil {
		return nil, err
	}
	// The persisted core log covers exactly one run and backs log exports.
	setCoreLogFile(filepath.Join(homeDir, "core.log"))
	// Anything the Go runtime writes on its way out - a panic, an abort - lands
	// in this file instead of Android's /dev/null.
	setStderrFile(filepath.Join(homeDir, "core-stderr.log"))

	// Keep the user's Mihomo configuration intact, but Android owns these TUN
	// fields because it created the interface and its descriptor.
	raw := map[string]any{}
	if err := yaml.Unmarshal([]byte(configText), &raw); err != nil {
		return nil, err
	}
	if raw == nil {
		raw = map[string]any{}
	}

	// Profile scripts run before UI overrides and Android-owned TUN fields.
	// Abort on error rather than silently connecting with a different routing policy.
	var scriptOptions map[string]any
	if overridesJson != "" {
		if err := json.Unmarshal([]byte(overridesJson), &scriptOptions); err != nil {
			return nil, err
		}
		if source, ok := scriptOptions["miku-override-script"].(string); ok && strings.TrimSpace(source) != "" {
			var err error
			raw, err = evaluateScript(raw, source, 2*time.Second)
			if err != nil {
				return nil, err
			}
		}
	}

	// Group selection/pinning is only restored across restarts when the config
	// opts in, so default it on unless the configuration says otherwise.
	if profile, ok := raw["profile"].(map[string]any); ok && profile != nil {
		if _, set := profile["store-selected"]; !set {
			profile["store-selected"] = true
		}
		if _, set := profile["store-fake-ip"]; !set {
			profile["store-fake-ip"] = true
		}
	} else {
		raw["profile"] = map[string]any{"store-selected": true, "store-fake-ip": true}
	}

	// Desktop-oriented keys that cannot work on Android: the OS reserves TCP/
	// UDP port 53 for system services, and unix controller paths point at the
	// desktop filesystem. TUN's dns-hijack already captures DNS traffic, so the
	// dns listener is redundant anyway.
	if dns, ok := raw["dns"].(map[string]any); ok && dns != nil {
		delete(dns, "listen")
	}
	delete(raw, "external-controller-unix")
	// The core calls os.Exit(2) when a profile asks for iptables together with
	// the TUN ("when tun is enabled, iptables cannot be set automatically"),
	// which an Android app cannot survive: the process would simply vanish with
	// nothing in logcat. The VPN interface owns routing here, so the key is
	// meaningless anyway.
	delete(raw, "iptables")

	// tunnel.Proxies() marshals alphabetically; remember the declared group
	// order so the app can present groups as the config author wrote them.
	order := []string{}
	if groups, ok := raw["proxy-groups"].([]any); ok {
		for _, group := range groups {
			if entry, ok := group.(map[string]any); ok {
				if name, ok := entry["name"].(string); ok && name != "" {
					order = append(order, name)
				}
			}
		}
	}

	if dnsOverride != "" {
		dns := map[string]any{}
		if err := yaml.Unmarshal([]byte(dnsOverride), &dns); err != nil {
			return nil, err
		}
		raw["dns"] = dns
	}

	// App-level overrides. A flat key replaces a top-level setting; "tun" and
	// "dns" carry objects that are merged into those sections, which is how the
	// app offers the same structured knobs the desktop clients do (stack, MTU,
	// fake-ip range, resolvers, filters) without rewriting the profile.
	var android4, android6 []netip.Prefix
	appendSystemDNS := false
	if overridesJson != "" {
		overrides := map[string]any{}
		if err := json.Unmarshal([]byte(overridesJson), &overrides); err == nil {
			log.Debugln("[Overrides] applying %d setting keys", len(overrides))
			for key, value := range overrides {
				switch key {
				case "miku-override-script":
					// Already evaluated above.
				case "miku-append-system-dns":
					appendSystemDNS, _ = value.(bool)
				case "miku-tun-ipv4", "miku-tun-ipv6":
					text, _ := value.(string)
					prefix, err := netip.ParsePrefix(text)
					if err != nil {
						return nil, err
					}
					if key == "miku-tun-ipv4" {
						android4 = []netip.Prefix{prefix}
					} else {
						android6 = []netip.Prefix{prefix}
					}
				case "miku-tls-verify":
					if verify, ok := value.(bool); ok {
						applyTLSVerification(raw, verify)
					}
				case "tun", "dns", "sniffer":
					mergeSection(raw, key, value)
				default:
					raw[key] = value
				}
			}
		} else {
			log.Warnln("[Overrides] ignored, not valid JSON: %s", err.Error())
		}
	}

	// The app owns the interface itself, so these TUN fields are applied last:
	// nothing a profile or an override says may move the descriptor or the
	// routes Android installed.
	if vpnMode {
		tun, ok := raw["tun"].(map[string]any)
		if !ok || tun == nil {
			tun = map[string]any{}
		}
		tun["enable"] = true
		tun["device"] = "MikuBox"
		if tunFD >= 0 {
			tun["file-descriptor"] = tunFD
		} else {
			delete(tun, "file-descriptor")
		}
		tun["auto-route"] = false
		tun["auto-detect-interface"] = false
		tun["strict-route"] = false
		// Respect an explicit empty list (hijacking disabled). Only supply
		// the safe Android default when neither profile nor UI selected a list.
		if _, exists := tun["dns-hijack"]; !exists {
			tun["dns-hijack"] = []string{"any:53"}
		}
		raw["tun"] = tun

		// A catch-all TUN captures every resolver query, so the core has to own
		// DNS. With it disabled - a profile that turns it off, or none at all -
		// those queries are still hijacked and then never answered, which
		// reaches the user as a connection without internet.
		dns, ok := raw["dns"].(map[string]any)
		if !ok || dns == nil {
			dns = map[string]any{}
		}
		dns["enable"] = true
		// mihomo refuses to start when "respect-rules" is on but no resolver for
		// node hostnames is set, which would otherwise leave the user with a VPN
		// that cannot connect at all. Fill in the usual bootstrap resolvers.
		if rules, _ := dns["respect-rules"].(bool); rules && emptyList(dns["proxy-server-nameserver"]) {
			dns["proxy-server-nameserver"] = []any{"119.29.29.29", "1.1.1.1", "8.8.8.8", "9.9.9.9"}
		}
		raw["dns"] = dns
	} else {
		// Proxy mode is the non-VPN counterpart of UwU's ProxyService.
		raw["tun"] = map[string]any{"enable": false}
		if _, configured := raw["mixed-port"]; !configured {
			raw["mixed-port"] = 7890
		}
	}

	core.effective = effectiveSummary(raw)
	// One line per start so a log export proves what the core was actually given,
	// including everything the app's settings overrode.
	if summary, err := json.Marshal(core.effective); err == nil {
		log.Infoln("[Config] effective: %s", string(summary))
	}

	configBytes, err := yaml.Marshal(raw)
	if err != nil {
		return nil, err
	}
	if err := hub.Parse(configBytes, func(cfg *config.Config) {
		if appendSystemDNS {
			cfg.DNS.NameServer = append(cfg.DNS.NameServer, dns.NameServer{Net: "system"})
		}
		if vpnMode && len(android4) > 0 {
			cfg.General.Tun.Inet4Address = android4
			cfg.General.Tun.Inet6Address = android6
		}
		if tunFD == deferredTunFD {
			pending := cfg.General.Tun
			core.pendingTun = &pending
			// Keep VPN-specific DNS/settings, but don't create an interface while
			// ApplyConfig is still loading providers and suspending the tunnel.
			cfg.General.Tun.Enable = false
		}
	}); err != nil {
		return nil, err
	}
	// hub.Parse reports success even when an inbound never came up: the TUN
	// listener logs its failure and disables itself instead of propagating the
	// error, which left the app announcing a healthy connection over a tunnel
	// nothing was reading. LastTunConf only keeps enable set when the listener
	// really started.
	if tunFD >= 0 && !listener.LastTunConf.Enable {
		return nil, errors.New("TUN listener failed to start, see the core log")
	}
	// The sampler measures from here on, so the app can report what this
	// connection consumed and how it was split across nodes and groups.
	startTrafficSampler()
	return order, nil
}

// mergeSection merges an app override object into one of the configuration's
// mapping sections, creating the section when the profile does not define it.
func mergeSection(raw map[string]any, name string, value any) {
	incoming, ok := value.(map[string]any)
	if !ok {
		return
	}
	section, ok := raw[name].(map[string]any)
	if !ok || section == nil {
		section = map[string]any{}
	}
	for key, entry := range incoming {
		if _, nested := entry.(map[string]any); nested {
			mergeSection(section, key, entry)
		} else {
			section[key] = entry
		}
	}
	raw[name] = section
}

// emptyList reports whether a config value is an absent or empty list.
func emptyList(value any) bool {
	switch typed := value.(type) {
	case nil:
		return true
	case []any:
		return len(typed) == 0
	case []string:
		return len(typed) == 0
	case string:
		return strings.TrimSpace(typed) == ""
	default:
		return false
	}
}

// effectiveSummary extracts the non-sensitive runtime settings of the config
// the core is about to receive, for log-export diagnostics.
func effectiveSummary(raw map[string]any) map[string]any {
	summary := map[string]any{}
	if tunCfg, ok := raw["tun"].(map[string]any); ok {
		summary["tun_enable"] = tunCfg["enable"]
		summary["tun_stack"] = tunCfg["stack"]
		summary["tun_mtu"] = tunCfg["mtu"]
		summary["tun_udp_timeout"] = tunCfg["udp-timeout"]
		summary["tun_icmp_timeout"] = tunCfg["icmp-timeout"]
		summary["tun_endpoint_nat"] = tunCfg["endpoint-independent-nat"]
		summary["tun_disable_icmp"] = tunCfg["disable-icmp-forwarding"]
		if _, set := tunCfg["dns-hijack"]; set {
			summary["tun_dns_hijack"] = tunCfg["dns-hijack"]
		}
	}
	if dnsCfg, ok := raw["dns"].(map[string]any); ok {
		summary["dns_enable"] = dnsCfg["enable"]
		summary["dns_mode"] = dnsCfg["enhanced-mode"]
		summary["dns_fake_ip_range"] = dnsCfg["fake-ip-range"]
		summary["dns_prefer_h3"] = dnsCfg["prefer-h3"]
		summary["dns_respect_rules"] = dnsCfg["respect-rules"]
		summary["dns_use_hosts"] = dnsCfg["use-hosts"]
		summary["dns_use_system_hosts"] = dnsCfg["use-system-hosts"]
		summary["dns_ipv6"] = dnsCfg["ipv6"]
		summary["dns_cache_algorithm"] = dnsCfg["cache-algorithm"]
		summary["dns_direct_follow_policy"] = dnsCfg["direct-nameserver-follow-policy"]
		for _, key := range []string{"nameserver", "default-nameserver", "proxy-server-nameserver", "direct-nameserver", "fallback", "fake-ip-filter"} {
			if entries, ok := dnsCfg[key].([]any); ok {
				summary["dns_"+strings.ReplaceAll(key, "-", "_")+"_count"] = len(entries)
			}
		}
		if listen, set := dnsCfg["listen"]; set {
			summary["dns_listen"] = listen
		}
	}
	summary["unified_delay"] = raw["unified-delay"]
	summary["tcp_concurrent"] = raw["tcp-concurrent"]
	summary["mode"] = raw["mode"]
	summary["mixed_port"] = raw["mixed-port"]
	summary["ipv6"] = raw["ipv6"]
	summary["allow_lan"] = raw["allow-lan"]
	summary["find_process_mode"] = raw["find-process-mode"]
	summary["geodata_loader"] = raw["geodata-loader"]
	summary["geodata_mode"] = raw["geodata-mode"]
	summary["client_fingerprint"] = raw["global-client-fingerprint"]
	summary["keep_alive_interval"] = raw["keep-alive-interval"]
	summary["disable_keep_alive"] = raw["disable-keep-alive"]
	if sniffer, ok := raw["sniffer"].(map[string]any); ok {
		summary["sniffer_enable"] = sniffer["enable"]
		summary["sniffer_override_destination"] = sniffer["override-destination"]
		if protocols, ok := sniffer["sniff"].(map[string]any); ok {
			summary["sniffer_protocols"] = len(protocols)
		}
	}
	if profile, ok := raw["profile"].(map[string]any); ok {
		summary["store_selected"] = profile["store-selected"]
		summary["store_fake_ip"] = profile["store-fake-ip"]
	}
	return summary
}

func main() {}

// A global TLS policy must reach node options and provider overrides; mihomo
// has no top-level skip-cert-verify option. Absent policy preserves the profile.
func applyTLSVerification(raw map[string]any, verify bool) {
	if proxies, ok := raw["proxies"].([]any); ok {
		for _, entry := range proxies {
			if proxy, ok := entry.(map[string]any); ok {
				proxy["skip-cert-verify"] = !verify
			}
		}
	}
	if providers, ok := raw["proxy-providers"].(map[string]any); ok {
		for _, entry := range providers {
			if provider, ok := entry.(map[string]any); ok {
				override, _ := provider["override"].(map[string]any)
				if override == nil {
					override = map[string]any{}
				}
				override["skip-cert-verify"] = !verify
				provider["override"] = override
			}
		}
	}
}
