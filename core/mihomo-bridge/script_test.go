package main

import (
	"testing"
	"time"
)

func TestOverrideScriptContract(t *testing.T) {
	for _, script := range []string{
		`function main(c) { c.rules.unshift('DOMAIN,example.com,DIRECT'); return c; }`,
		`const main = c => ({...c, rules: ['DOMAIN,example.com,DIRECT', ...c.rules]});`,
		`async function main(c) { await Promise.resolve(); c.rules.unshift('DOMAIN,example.com,DIRECT'); return c; }`,
	} {
		original := map[string]any{"rules": []any{"MATCH,REJECT"}}
		result, err := evaluateScript(original, script, time.Second)
		if err != nil {
			t.Fatal(err)
		}
		if result["rules"].([]any)[0] != "DOMAIN,example.com,DIRECT" {
			t.Fatal(result)
		}
		if len(original["rules"].([]any)) != 1 {
			t.Fatal("modified imported profile")
		}
	}
}

func TestOverrideScriptRejectsInvalidAndUnsettledResults(t *testing.T) {
	for _, script := range []string{
		`function main( {`, `const x = 1`, `function main(){ return [] }`,
		`function main(){ return 42 }`, `function main(){ throw Error('bad') }`,
		`function main(){ const a={}; a.self=a; return a }`,
		`async function main(){ throw Error('bad promise') }`,
		`function main(){ return new Promise(() => {}) }`,
		`function main(){ return require('fs') }`,
	} {
		if _, err := evaluateScript(map[string]any{}, script, time.Second); err == nil {
			t.Fatalf("accepted %s", script)
		}
	}
}

func TestOverrideScriptTimeoutAndIsolation(t *testing.T) {
	started := time.Now()
	if _, err := evaluateScript(map[string]any{}, `function main(){while(true){}}`, 20*time.Millisecond); err == nil {
		t.Fatal("loop not interrupted")
	}
	if time.Since(started) > time.Second {
		t.Fatal("timeout not bounded")
	}
	if _, err := evaluateScript(map[string]any{}, `globalThis.leaked=1; function main(c){return c}`, time.Second); err != nil {
		t.Fatal(err)
	}
	if _, err := evaluateScript(map[string]any{}, `function main(c){if(typeof leaked !== 'undefined') throw Error('leaked'); return c}`, time.Second); err != nil {
		t.Fatal(err)
	}
	result, err := evaluateScript(map[string]any{"mode": "rule"}, `function main(c){c.mode='global'}`, time.Second)
	if err != nil || result["mode"] != "rule" {
		t.Fatal("undefined return must preserve original", result, err)
	}
}
