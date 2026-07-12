#!/bin/bash

chmod -R 777 .build 2>/dev/null
rm -rf .build 2>/dev/null

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# The sing-box submodule is bumped frequently and each bump pulls newer
# transitive deps than libcore/go.mod pins. Go's default -mod=readonly then
# aborts the gomobile build with "updates to go.mod needed; run go mod tidy"
# (fine locally off a warm module cache, but fatal on a fresh CI checkout).
# Resync go.mod/go.sum against the checked-out submodule before building.
go mod tidy || exit 1

# sing-box's own gomobile fork. Upstream golang.org/x/mobile lacks the -libname
# flag and the binding fixes libbox depends on; sing-box pins this version in
# its Makefile (lib_install).
if [ ! -f "$GOPATH/bin/gomobile" ]; then
    go install -v github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
    go install -v github.com/sagernet/gomobile/cmd/gobind@v0.1.13
fi

"$GOPATH"/bin/gomobile init
