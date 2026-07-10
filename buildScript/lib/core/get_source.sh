#!/bin/bash
set -e

source "buildScript/init/env.sh"

# Initialize submodules (sing-box is managed as a submodule)
git submodule update --init --recursive

echo "sing-box source is at: $(git -C sing-box rev-parse HEAD 2>/dev/null || echo 'not found')"
