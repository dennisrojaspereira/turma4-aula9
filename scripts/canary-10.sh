#!/usr/bin/env bash
# Canary em 10%. Ver canary.sh.
exec "$(dirname "$0")/canary.sh" 10
