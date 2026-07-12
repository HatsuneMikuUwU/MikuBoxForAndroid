package libcore

import (
	"context"
	"fmt"
	"libcore/device"
	"log"
	"runtime"
	"runtime/debug"
	"strings"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/experimental/libbox"
	"github.com/sagernet/sing-box/experimental/v2rayapi"
	"github.com/sagernet/sing-box/option"
	"google.golang.org/protobuf/types/known/emptypb"
)

func VersionBox() string {
	version := []string{
		"sing-box: " + constant.Version,
		runtime.Version() + "@" + runtime.GOOS + "/" + runtime.GOARCH,
	}

	var tags string
	debugInfo, loaded := debug.ReadBuildInfo()
	if loaded {
		for _, setting := range debugInfo.Settings {
			switch setting.Key {
			case "-tags":
				tags = setting.Value
			}
		}
	}

	if tags != "" {
		version = append(version, tags)
	}

	return strings.Join(version, "\n")
}

func ResetAllConnections(system bool) {
	if commandServer == nil {
		return
	}
	if system {
		commandServer.ResetNetwork()
		log.Println("Reset system connections done")
		return
	}
	_, err := commandServer.StartedService.CloseAllConnections(context.Background(), &emptypb.Empty{})
	if err != nil {
		log.Println("Reset user connections:", err)
	}
}

// --- CommandServer management ---

var commandServer *libbox.CommandServer

func NewCommandServer(handler libbox.CommandServerHandler, platform libbox.PlatformInterface) *libbox.CommandServer {
	var err error
	commandServer, err = libbox.NewCommandServer(handler, platform)
	if err != nil {
		log.Println("NewCommandServer error:", err)
		return nil
	}
	return commandServer
}

func StartCommandServer() error {
	if commandServer == nil {
		return fmt.Errorf("command server not created")
	}
	return commandServer.Start()
}

func StartOrReloadService(configContent string, options *libbox.OverrideOptions) error {
	// A reload builds a new router; the old tracker stays attached to the old
	// one. Drop it so the next SetV2rayStats re-attaches.
	v2api = nil
	// Starting the service builds the gVisor TUN stack. This entrypoint is
	// invoked through JNI, so its goroutine stack is pinned to the host pthread
	// size (8188KB on Android); gVisor's initialization overruns that bound and
	// aborts the process with "stack size 8188KB". Re-dispatch onto a fresh
	// goroutine, whose Go-managed stack grows on demand. Mirrors sing-box's
	// FixAndroidStack workaround (golang.org/go#68760).
	return runOnFreshStack(func() error {
		return commandServer.StartOrReloadService(configContent, options)
	})
}

func CloseService() error {
	v2api = nil
	// Tearing down the gVisor TUN stack runs the same deep code on the
	// JNI-entered goroutine, so keep it off the capped stack as well.
	return runOnFreshStack(func() error {
		return commandServer.CloseService()
	})
}

// runOnFreshStack runs fn on a newly spawned goroutine and returns its result.
// Unlike the goroutine Go creates to service an incoming JNI/cgo call, a fresh
// goroutine's stack is not bounded by the host thread's pthread stack size and
// grows on demand, avoiding the Android "stack size 8188KB" abort.
func runOnFreshStack(fn func() error) error {
	result := make(chan error, 1)
	go func() {
		defer device.DeferPanicToError("box.runOnFreshStack", func(err error) { result <- err })
		result <- fn()
	}()
	return <-result
}

func CloseCommandServer() {
	if commandServer != nil {
		commandServer.Close()
	}
}

func PauseService() {
	if commandServer != nil {
		commandServer.Pause()
	}
}

func WakeService() {
	if commandServer != nil {
		commandServer.Wake()
	}
}

func SelectOutbound(groupTag string, outboundTag string) error {
	_, err := commandServer.StartedService.SelectOutbound(
		context.Background(),
		&daemon.SelectOutboundRequest{
			GroupTag:    groupTag,
			OutboundTag: outboundTag,
		},
	)
	return err
}

// UrlTestGroup triggers an asynchronous URL test on an outbound group of the
// running service. The daemon reports results through SubscribeStatus; it does
// not return a latency. For a latency measurement use UrlTest / UrlTestMain.
func UrlTestGroup(groupTag string) (err error) {
	defer device.DeferPanicToError("box.UrlTestGroup", func(err_ error) { err = err_ })
	if commandServer == nil {
		return fmt.Errorf("command server not created")
	}
	_, err = commandServer.StartedService.URLTest(
		context.Background(),
		&daemon.URLTestRequest{
			OutboundTag: groupTag,
		},
	)
	return err
}

// runningBox returns the box of the service started through the CommandServer,
// or nil when no service is running.
func runningBox() *box.Box {
	if commandServer == nil {
		return nil
	}
	instance := commandServer.StartedService.Instance()
	if instance == nil {
		return nil
	}
	return instance.Box()
}

// v2api tracks per-outbound traffic. libbox's TrafficManager only reports
// global totals, so the v2ray stats service is attached to the running router
// the same way the old boxapi.SbV2rayServer was.
var v2api *v2rayapi.StatsService

func SetV2rayStats(outbounds string) {
	instance := runningBox()
	if instance == nil {
		return
	}
	if v2api != nil {
		log.Println("duplicate call of SetV2rayStats")
		return
	}
	v2api = v2rayapi.NewStatsService(option.V2RayStatsServiceOptions{
		Enabled:   true,
		Outbounds: strings.Split(outbounds, "\n"),
	})
	if v2api == nil {
		return
	}
	instance.Router().AppendTracker(v2api)
}

func QueryStats(tag, direct string) int64 {
	if v2api == nil {
		return 0
	}
	response, err := v2api.GetStats(context.Background(), &v2rayapi.GetStatsRequest{
		Name:   fmt.Sprintf("outbound>>>%s>>>traffic>>>%s", tag, direct),
		Reset_: true,
	})
	if err != nil {
		return 0
	}
	return response.Stat.Value
}
