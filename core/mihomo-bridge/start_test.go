package main

import (
	"os"
	"path/filepath"
	"testing"
)

// Android app processes run with "/" as the working directory and it is not
// writable, which used to break config.Init's relative config.yaml write.
func TestStartWithReadOnlyWorkingDirectory(t *testing.T) {
	original, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd: %v", err)
	}
	defer os.Chdir(original)
	if err := os.Chdir("/"); err != nil {
		t.Fatalf("chdir: %v", err)
	}

	home := t.TempDir()
	config := "mode: rule\nmixed-port: 17890\nrules:\n  - MATCH,DIRECT\n"
	if _, err := start(config, home, -1, "", ""); err != nil {
		t.Fatalf("start failed: %v", err)
	}
	defer shutdownCore()

	if _, err := os.Stat(filepath.Join(home, "config.yaml")); err != nil {
		t.Fatalf("initial config.yaml not created in home dir: %v", err)
	}
}
