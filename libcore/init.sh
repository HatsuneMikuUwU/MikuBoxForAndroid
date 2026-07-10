#!/bin/bash

chmod -R 777 .build 2>/dev/null
rm -rf .build 2>/dev/null

if [ -z "$GOPATH" ]; then
    GOPATH=$(go env GOPATH)
fi

# sing-box's own gomobile fork. Upstream golang.org/x/mobile lacks the -libname
# flag and the binding fixes libbox depends on; sing-box pins this version in
# its Makefile (lib_install).
if [ ! -f "$GOPATH/bin/gomobile" ]; then
    go install -v github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
    go install -v github.com/sagernet/gomobile/cmd/gobind@v0.1.13
fi

"$GOPATH"/bin/gomobile init
