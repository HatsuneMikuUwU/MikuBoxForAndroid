//go:build !windows

package sing_tun

import (
	tun "github.com/metacubex/sing-tun"
	"golang.org/x/sys/unix"
	"runtime"
)

// Android borrows VpnService's descriptor during startup. The listener owns
// only its duplicate, including when stack initialization subsequently fails.
func tunNew(options tun.Options) (tun.Tun, error) {
	if runtime.GOOS != "android" || options.FileDescriptor <= 0 {
		return tun.New(options)
	}
	fd, err := unix.Dup(options.FileDescriptor)
	if err != nil {
		return nil, err
	}
	unix.CloseOnExec(fd)
	options.FileDescriptor = fd
	device, err := tun.New(options)
	if err != nil {
		_ = unix.Close(fd)
	}
	return device, err
}
