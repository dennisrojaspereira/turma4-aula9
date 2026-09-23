#!/usr/bin/env bash
# Apaga o cluster kind "techpix". Nao toca em outros clusters.
set -euo pipefail
kind delete cluster --name techpix
