# Android TUN descriptor ownership

`app:buildMihomoBridge` uses a Go build overlay to replace Mihomo's
`listener/sing_tun/server_notwindows.go` with this local file. The submodule
remains unchanged. Every Android ABI is built with the same overlay.

The Android service lends its detached descriptor for the duration of native
startup and closes it after the call, on success or failure. Just before
sing-tun constructs its device, this adapter duplicates that descriptor. The
listener owns only that duplicate and closes it during failure cleanup or stop.
Errors before constructing the listener do not acquire a duplicate.

The pinned sing-tun v0.4.21 Android/Linux constructor with FileDescriptor > 0
adopts the supplied descriptor without an error path; keep this ownership
contract under review when upgrading the dependency. Non-Android builds retain
the original constructor behavior.

## gVisor detach

`stack_gvisor_filter.go` replaces sing-tun v0.4.21's packet filter in a generated
copy under `app/build/patched`, selected by a separate Go modfile. Its upstream
`Attach(nil)` wraps the nil dispatcher, hiding the detach signal from gVisor's
FD endpoint. The endpoint consequently never stops and joins its packet readers.
After closing the Android descriptor, an idle `ppoll` can retain the TUN file
until the next packet, leaving VPN routes active without a working core.

Preserve nil when detaching; keep the existing filter for a live dispatcher.
The module cache is never edited. `./gradlew :app:testMihomoBridge` and every ABI
build consume the same generated module and overlay, and the regression test checks that
the underlying endpoint receives a real nil dispatcher.
