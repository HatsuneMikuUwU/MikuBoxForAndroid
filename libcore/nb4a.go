package libcore

import (
	"fmt"
	"io"
	"libcore/device"
	"log"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"
	"sync"

	"github.com/sagernet/sing-box/experimental/libbox"
	"github.com/sagernet/sing-box/option"
	"golang.org/x/sys/unix"
)

var (
	nekoLogMu   sync.Mutex
	nekoLogFile *os.File
	nekoLogPath string
)

const nekoLogMaxBytes int64 = 512 * 1024

func initNekoLog(cachePath string, allowTrim bool) {
	nekoLogMu.Lock()
	defer nekoLogMu.Unlock()
	if nekoLogFile != nil {
		return
	}
	nekoLogPath = filepath.Join(cachePath, "neko.log")
	if allowTrim {
		if info, err := os.Stat(nekoLogPath); err == nil && info.Size() > nekoLogMaxBytes {
			_ = os.Truncate(nekoLogPath, 0)
		}
	}
	f, err := os.OpenFile(nekoLogPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return
	}
	nekoLogFile = f
	log.SetOutput(io.MultiWriter(os.Stderr, f))
}

func NekoLogPrintln(s string) {
	log.Println(s)
}

func NekoLogClear() {
	nekoLogMu.Lock()
	defer nekoLogMu.Unlock()
	if nekoLogPath != "" {
		_ = os.Truncate(nekoLogPath, 0)
	}
	if nekoLogFile != nil {
		_, _ = nekoLogFile.Seek(0, 0)
	}
}

func ForceGc() {
	go debug.FreeOSMemory()
}

func Setup(process, cachePath, internalAssets, externalAssets string,
	maxLogSizeKb int32, logEnable bool,
) {
	defer device.DeferPanicToError("Setup", func(err error) { log.Println(err) })
	isBgProcess = strings.HasSuffix(process, ":bg")
	externalAssetsPath = externalAssets
	internalAssetsPath = internalAssets

	// Working dir
	tmp := filepath.Join(cachePath, "../no_backup")
	os.MkdirAll(tmp, 0755)
	os.Chdir(tmp)
	initNekoLog(cachePath, isBgProcess)

	// Initialize libbox
	logMaxLines := int(maxLogSizeKb)
	if logMaxLines < 50 {
		logMaxLines = 50
	}
	err := libbox.Setup(&libbox.SetupOptions{
		BasePath:         cachePath,
		WorkingPath:      filepath.Join(cachePath, "working"),
		TempPath:         tmp,
		FixAndroidStack:  true,
		Debug:            logEnable,
		LogMaxLines:      logMaxLines,
	})
	if err != nil {
		log.Println("libbox.Setup error:", err)
	}

	if rawQueryFunc != nil {
		gLocalDNSTransport = newAndroidLocalTransport("local", option.LocalDNSServerOptions{})
	}

	// The remaining initialization does not call Android/JNI APIs and can run
	// asynchronously.
	go func() {
		defer device.DeferPanicToError("Setup-go", func(err error) { log.Println(err) })
		device.GoDebug(process)

		pem, err := os.ReadFile(externalAssets + "ca.pem")
		if err == nil {
			updateRootCACerts(pem)
		}

	}()
}

func sendFdToProtect(fd int, path string) error {
	socketFd, err := unix.Socket(unix.AF_UNIX, unix.SOCK_STREAM, 0)
	if err != nil {
		return fmt.Errorf("failed to create unix socket: %w", err)
	}
	defer unix.Close(socketFd)

	var timeout unix.Timeval
	timeout.Usec = 100 * 1000

	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &timeout)
	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_SNDTIMEO, &timeout)

	err = unix.Connect(socketFd, &unix.SockaddrUnix{Name: path})
	if err != nil {
		return fmt.Errorf("failed to connect: %w", err)
	}

	err = unix.Sendmsg(socketFd, nil, unix.UnixRights(fd), nil, 0)
	if err != nil {
		return fmt.Errorf("failed to send: %w", err)
	}

	dummy := []byte{1}
	n, err := unix.Read(socketFd, dummy)
	if err != nil {
		return fmt.Errorf("failed to receive: %w", err)
	}
	if n != 1 {
		return fmt.Errorf("socket closed unexpectedly")
	}
	return nil
}
