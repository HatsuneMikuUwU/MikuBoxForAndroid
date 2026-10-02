package main

/*
#include <stdlib.h>
typedef int (*socket_protector)(int);
static inline int protect_socket(void* fn, int fd) { return ((socket_protector)fn)(fd); }
*/
import "C"

import (
	"errors"
	"github.com/metacubex/mihomo/component/dialer"
	"syscall"
	"unsafe"
)

//export MihomoSetSocketProtector
func MihomoSetSocketProtector(callback unsafe.Pointer) {
	dialer.DefaultSocketHook = func(network, address string, conn syscall.RawConn) error {
		if callback == nil {
			return errors.New("Android socket protector unavailable")
		}
		var protected bool
		if err := conn.Control(func(fd uintptr) { protected = C.protect_socket(callback, C.int(fd)) != 0 }); err != nil {
			return err
		}
		if !protected {
			return errors.New("Android could not protect/bind outbound socket")
		}
		return nil
	}
}
