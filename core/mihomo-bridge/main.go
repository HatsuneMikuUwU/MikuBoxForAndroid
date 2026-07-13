package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"encoding/json"
	"sync"

	"github.com/metacubex/mihomo/common/yaml"
	"github.com/metacubex/mihomo/config"
	constant "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub"
	"github.com/metacubex/mihomo/hub/executor"
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
