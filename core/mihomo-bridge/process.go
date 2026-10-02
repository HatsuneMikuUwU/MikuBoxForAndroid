package main

/*
#include <stdlib.h>
typedef char* (*package_resolver)(int, const char*, int, const char*, int, int);
static inline char* resolve_package(void* fn, int protocol, const char* src, int sp, const char* dst, int dp, int uid) {
    return ((package_resolver)fn)(protocol, src, sp, dst, dp, uid);
}
*/
import "C"

import (
	"github.com/metacubex/mihomo/component/process"
	constant "github.com/metacubex/mihomo/constant"
	"net"
	"strconv"
	"strings"
	"unsafe"
)

//export MihomoSetProcessResolver
func MihomoSetProcessResolver(callback unsafe.Pointer) {
	process.DefaultPackageNameResolver = func(metadata *constant.Metadata) (string, error) {
		if callback == nil {
			return "", process.ErrPlatformNotSupport
		}
		protocol := 6
		if metadata.NetWork == constant.UDP {
			protocol = 17
		}
		destination, destinationPort := socketDestination(metadata)
		src, dst := C.CString(metadata.SrcIP.String()), C.CString(destination)
		defer C.free(unsafe.Pointer(src))
		defer C.free(unsafe.Pointer(dst))
		result := C.resolve_package(callback, C.int(protocol), src, C.int(metadata.SrcPort), dst, C.int(destinationPort), C.int(metadata.Uid))
		if result == nil {
			return "", process.ErrNotFound
		}
		defer C.free(unsafe.Pointer(result))
		parts := strings.SplitN(C.GoString(result), "\n", 2)
		if len(parts) != 2 || parts[1] == "" {
			return "", process.ErrNotFound
		}
		uid, err := strconv.ParseUint(parts[0], 10, 32)
		if err != nil {
			return "", err
		}
		metadata.Uid = uint32(uid)
		return parts[1], nil
	}
}

// Use the socket destination before Fake-IP resolution clears/replaces DstIP.
func socketDestination(metadata *constant.Metadata) (string, int) {
	if metadata.RawDstAddr != nil {
		if host, port, err := net.SplitHostPort(metadata.RawDstAddr.String()); err == nil {
			if number, err := strconv.Atoi(port); err == nil {
				return host, number
			}
		}
	}
	return metadata.DstIP.String(), int(metadata.DstPort)
}
