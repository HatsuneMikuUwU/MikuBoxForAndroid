module top.uwu.mikubox/mihomo-bridge

// Keep the wrapper's module graph unpruned: Mihomo itself supplies the full
// dependency graph and is pinned by the submodule.
go 1.16

// HSSkyBoy/mihomo's own go.mod keeps this compatibility module path.
require github.com/metacubex/mihomo v0.0.0

// The source is the HSSkyBoy/mihomo Alpha Git submodule, never the upstream
// MetaCubeX remote.
replace github.com/metacubex/mihomo => ../mihomo
