package libcore

import (
	"github.com/sagernet/sing-box/experimental/libbox"
)

// Go-side references to Kotlin implementations
var intfPlatform libbox.PlatformInterface
var intfHandler libbox.CommandServerHandler
var intfLocalDNSTransport libbox.LocalDNSTransport

var isBgProcess bool

// SetUseOfficialAssets tells the Go side whether to use official assets.
// Called from Kotlin during initialization.
var useOfficialAssets bool

func SetUseOfficialAssets(v bool) {
	useOfficialAssets = v
}
