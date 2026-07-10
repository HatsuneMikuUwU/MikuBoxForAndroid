package libcore

import (
	"fmt"
	"libcore/device"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"

	"log"

	"github.com/sagernet/sing-box/experimental/libbox"
	"github.com/sagernet/sing-box/option"
	"golang.org/x/sys/unix"
)

func NekoLogPrintln(s string) {
	log.Println(s)
}

func NekoLogClear() {
	// no-op with official libbox; logs managed internally
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

	// Initialize libbox
	logMaxLines := int(maxLogSizeKb)
	if logMaxLines < 50 {
		logMaxLines = 50
	}
	err := libbox.Setup(&libbox.SetupOptions{
		BasePath:    cachePath,
		WorkingPath: filepath.Join(cachePath, "working"),
		TempPath:    tmp,
		Debug:       logEnable,
		LogMaxLines: logMaxLines,
	})
	if err != nil {
		log.Println("libbox.Setup error:", err)
	}

	if rawQueryFunc != nil {
		gLocalDNSTransport = newAndroidLocalTransport("local", option.LocalDNSServerOptions{})
	}

	// Load custom CA certs + extract assets
	go func() {
		defer device.DeferPanicToError("Setup-go", func(err error) { log.Println(err) })
		device.GoDebug(process)

		pem, err := os.ReadFile(externalAssets + "ca.pem")
		if err == nil {
			updateRootCACerts(pem)
		}

		if isBgProcess {
			extractAssets()
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
