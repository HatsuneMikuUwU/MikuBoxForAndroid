package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"context"
	"encoding/json"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/component/profile/cachefile"
	"github.com/metacubex/mihomo/config"
	constant "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/executor"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
)

var core = struct {
	sync.Mutex
	running bool
	lastErr string
}{}

// MihomoStart initializes the Alpha core in-process. The Android app owns the
// VPN interface and passes its already-open descriptor to Mihomo's TUN inbound.
//
//export MihomoStart
func MihomoStart(configText *C.char, homeDir *C.char, tunFD C.int) C.int {
	core.Lock()
	defer core.Unlock()

	if core.running {
		executor.Shutdown()
		core.running = false
	}

	err := start(C.GoString(configText), C.GoString(homeDir), int(tunFD))
	if err != nil {
		core.lastErr = err.Error()
		return 1
	}

	core.lastErr = ""
	core.running = true
	return 0
}

// MihomoStop closes listeners before Android closes its ParcelFileDescriptor.
//
//export MihomoStop
func MihomoStop() {
	core.Lock()
	defer core.Unlock()
	if core.running {
		executor.Shutdown()
		core.running = false
	}
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
// HTTP controller to be enabled in a user configuration.
//
//export MihomoTraffic
func MihomoTraffic() *C.char {
	uplink, downlink := statistic.DefaultManager.Now()
	uploadTotal, downloadTotal := statistic.DefaultManager.Total()
	payload, _ := json.Marshal(struct {
		Upload        int64 `json:"upload"`
		Download      int64 `json:"download"`
		UploadTotal   int64 `json:"uploadTotal"`
		DownloadTotal int64 `json:"downloadTotal"`
	}{uplink, downlink, uploadTotal, downloadTotal})
	return C.CString(string(payload))
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
// PUT /proxies/{group}. Returns 0 on success, 1 on failure (see MihomoLastError).
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
	if err := selector.Set(name); err != nil {
		core.lastErr = err.Error()
		return 1
	}
	cachefile.Cache().SetSelected(group, name)
	core.lastErr = ""
	return 0
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

func start(configText, homeDir string, tunFD int) error {
	constant.SetHomeDir(homeDir)
	if err := config.Init(homeDir); err != nil {
		return err
	}

	// Keep the user's Mihomo configuration intact, but Android owns these TUN
	// fields because it created the interface and its descriptor.
	raw := map[string]any{}
	if err := yaml.Unmarshal([]byte(configText), &raw); err != nil {
		return err
	}
	if raw == nil {
		raw = map[string]any{}
	}

	if tunFD > 0 {
		tun, ok := raw["tun"].(map[string]any)
		if !ok || tun == nil {
			tun = map[string]any{}
		}
		tun["enable"] = true
		tun["device"] = "MikuBox"
		tun["stack"] = "system"
		tun["file-descriptor"] = tunFD
		tun["auto-route"] = false
		tun["auto-detect-interface"] = false
		tun["strict-route"] = false
		raw["tun"] = tun
	} else {
		// Proxy mode is the non-VPN counterpart of UwU's ProxyService.
		raw["tun"] = map[string]any{"enable": false}
		if _, configured := raw["mixed-port"]; !configured {
			raw["mixed-port"] = 7890
		}
	}

	configBytes, err := yaml.Marshal(raw)
	if err != nil {
		return err
	}
	return hub.Parse(configBytes)
}

func main() {}
