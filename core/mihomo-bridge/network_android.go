//go:build android && cmfa

package main

/*
#include <stdlib.h>
*/
import "C"
import (
	"encoding/json"
	"github.com/metacubex/mihomo/dns"
)

//export MihomoUpdateSystemDNS
func MihomoUpdateSystemDNS(payload *C.char) {
	var addresses []string
	if json.Unmarshal([]byte(C.GoString(payload)), &addresses) == nil {
		dns.UpdateSystemDNS(addresses)
	}
}
