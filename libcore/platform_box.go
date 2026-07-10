package libcore

import (
	"strings"

	sblog "github.com/sagernet/sing-box/log"
)

// PlatformWriter for sing-box log output (used by the daemon log system)
// With official libbox, logs are captured by CommandServer/StartedService
// and streamed via gRPC SubscribeLog. This writer is kept for backward
// compatibility if any code still writes log entries directly.

type boxPlatformLogWriterWrapper struct{}

var boxPlatformLogWriter sblog.PlatformWriter = &boxPlatformLogWriterWrapper{}

func (w *boxPlatformLogWriterWrapper) DisableColors() bool { return true }

func (w *boxPlatformLogWriterWrapper) WriteMessage(level uint8, message string) {
	if !strings.HasSuffix(message, "\n") {
		message += "\n"
	}
	// Forward to the command server log if available
	if commandServer != nil {
		commandServer.WriteMessage(int32(level), message)
	}
}
