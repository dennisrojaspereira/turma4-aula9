#!/usr/bin/env bash
# Canary em 50%. Ver canary.sh.
exec "$(dirname "$0")/canary.sh" 50
