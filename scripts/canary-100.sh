#!/usr/bin/env bash
# Canary em 100%. Ver canary.sh.
exec "$(dirname "$0")/canary.sh" 100
