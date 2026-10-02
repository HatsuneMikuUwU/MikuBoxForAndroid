//go:build android

package main

/*
#include <android/log.h>
#include <stdlib.h>

static void miku_log_write(int prio, const char* tag, const char* msg) {
	__android_log_write(prio, tag, msg);
}
*/
import "C"

import (
	"os"
	"sync"
	"unsafe"

	"github.com/sirupsen/logrus"
	"golang.org/x/sys/unix"
)

const androidLogTag = "MikuBox"

// coreLogMaxBytes bounds the persisted core log; it is truncated in place once
// the limit is reached so an export always carries the latest session.
const coreLogMaxBytes = 1 << 20

var (
	coreLogMu   sync.Mutex
	coreLogFile *os.File
)

// androidLogWriter forwards the core's structured log to logcat and to a file.
// logcat keeps only a few seconds of history on chatty devices, so the file is
// what log exports read for a failed connection attempt.
type androidLogWriter struct{}

func (androidLogWriter) Write(p []byte) (int, error) {
	message := C.CString(string(p))
	tag := C.CString(androidLogTag)
	C.miku_log_write(C.ANDROID_LOG_INFO, tag, message)
	C.free(unsafe.Pointer(message))
	C.free(unsafe.Pointer(tag))

	coreLogMu.Lock()
	if coreLogFile != nil {
		if info, err := coreLogFile.Stat(); err == nil && info.Size() > coreLogMaxBytes {
			_ = coreLogFile.Truncate(0)
			_, _ = coreLogFile.Seek(0, 0)
		}
		_, _ = coreLogFile.Write(p)
	}
	coreLogMu.Unlock()
	return len(p), nil
}

// setCoreLogFile starts a fresh persisted log for the session that is starting.
func setCoreLogFile(path string) {
	coreLogMu.Lock()
	defer coreLogMu.Unlock()
	if coreLogFile != nil {
		_ = coreLogFile.Close()
		coreLogFile = nil
	}
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err == nil {
		coreLogFile = file
	}
}

// Package initialization order guarantees the mihomo log package has already
// pointed logrus at stdout, so this override takes effect for every core log.
func init() {
	logrus.SetOutput(androidLogWriter{})
}

// setStderrFile redirects the process' stderr into [path].
//
// An Android app's stderr is /dev/null, so a Go panic or any other runtime
// abort inside the core would take the whole app down without a single line in
// logcat - the user just sees the app vanish. The runtime writes panics straight
// to the descriptor rather than through os.Stderr, so the file has to be dup'd
// over fd 2.
func setStderrFile(path string) {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		return
	}
	unix.Dup2(int(file.Fd()), 2)
}
