#!/bin/bash

source ./env_java.sh || true
source ../buildScript/init/env_ndk.sh

BUILD=".build"

# gomobile does not run sing-box's build_libbox helper, which normally sets
# constant.Version. Preserve the checked-out core revision in the About page.
SING_BOX_VERSION=$(git -C ../sing-box describe --tags --always --dirty 2>/dev/null || echo unknown)

rm -rf $BUILD/android \
  $BUILD/java \
  $BUILD/javac-output \
  $BUILD/src

if [ -z "$GOPATH" ]; then
  GOPATH=$(go env GOPATH)
fi

# libbox is bound alongside libcore: NativeInterface/LocalResolverImpl implement
# libbox.PlatformInterface and libbox.LocalDNSTransport, and libcore exports
# functions taking those types. gomobile can only marshal foreign package types
# when that package is bound in the same invocation.
#
# -checklinkname=0 and the badlinkname/tfogo_checklinkname0 tags are required to
# link sing-box 1.14 (tfo-go and tailscale rely on //go:linkname).
"$GOPATH"/bin/gomobile bind -v -androidapi 21 -cache "$(realpath $BUILD)" -trimpath \
  -ldflags="-s -w -checklinkname=0 -X github.com/sagernet/sing-box/constant.Version=${SING_BOX_VERSION}" \
  -tags='with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,badlinkname,tfogo_checklinkname0' \
  -o libcore.aar \
  . github.com/sagernet/sing-box/experimental/libbox || exit 1
rm -f libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
