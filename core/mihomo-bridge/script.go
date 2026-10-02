package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"encoding/json"
	"fmt"
	"reflect"
	"time"

	"github.com/dop251/goja"
	"gopkg.in/yaml.v3"
)

// Scripts see JSON data only: no filesystem, network, Java or Go objects.
// A fresh runtime for every evaluation prevents cross-profile state leakage.
func evaluateScript(raw map[string]any, source string, timeout time.Duration) (map[string]any, error) {
	if len(source) > 256*1024 {
		return nil, fmt.Errorf("script exceeds 256 KiB")
	}
	input, err := json.Marshal(raw)
	if err != nil {
		return nil, err
	}
	vm := goja.New()
	vm.SetMaxCallStackSize(256)
	timer := time.AfterFunc(timeout, func() { vm.Interrupt("script timed out") })
	defer timer.Stop()
	// JSON is injected as a string and parsed inside JS, never as a reflected Go map.
	if err = vm.Set("__inputJSON", string(input)); err != nil {
		return nil, err
	}
	if _, err = vm.RunString(`const console = Object.freeze({log(){},info(){},warn(){},error(){},debug(){}}); const __config = JSON.parse(__inputJSON); if (!__config['proxy-providers']) __config['proxy-providers'] = {};`); err != nil {
		return nil, err
	}
	if _, err = vm.RunScript("override.js", source); err != nil {
		return nil, fmt.Errorf("override script: %w", err)
	}
	value, err := vm.RunString(`main(__config)`)
	if err != nil {
		return nil, fmt.Errorf("override main: %w", err)
	}
	if value.ExportType() == reflect.TypeOf((*goja.Promise)(nil)) {
		promise := value.Export().(*goja.Promise)
		switch promise.State() {
		case goja.PromiseStateFulfilled:
			value = promise.Result()
		case goja.PromiseStateRejected:
			return nil, fmt.Errorf("override rejected: %s", promise.Result().String())
		default:
			return nil, fmt.Errorf("override returned an unsettled Promise; timers and I/O are unavailable")
		}
	}
	if goja.IsUndefined(value) || goja.IsNull(value) {
		return raw, nil
	}
	if err = vm.Set("__result", value); err != nil {
		return nil, err
	}
	value, err = vm.RunString(`if (typeof __result !== 'object' || Array.isArray(__result)) throw new Error('main must return a configuration object'); JSON.stringify(__result)`)
	if err != nil {
		return nil, fmt.Errorf("override result: %w", err)
	}
	output := value.String()
	if len(output) > 16*1024*1024 {
		return nil, fmt.Errorf("script output exceeds 16 MiB")
	}
	var result map[string]any
	if err = json.Unmarshal([]byte(output), &result); err != nil || result == nil {
		return nil, fmt.Errorf("main must return a JSON configuration object")
	}
	return result, nil
}

//export MihomoEvaluateScript
func MihomoEvaluateScript(configText, source *C.char) *C.char {
	response := map[string]any{}
	var raw map[string]any
	err := yaml.Unmarshal([]byte(C.GoString(configText)), &raw)
	if err == nil {
		if raw == nil {
			raw = map[string]any{}
		}
		var result map[string]any
		result, err = evaluateScript(raw, C.GoString(source), 2*time.Second)
		if err == nil {
			response["config"] = result
		}
	}
	if err != nil {
		response["error"] = err.Error()
	}
	data, _ := json.Marshal(response)
	return C.CString(string(data))
}
