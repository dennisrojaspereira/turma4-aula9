# Cria o cluster kind "techpix", carrega as imagens e aplica os manifests (equivalente a k8s-up.sh).
#   .\scripts\k8s-up.ps1                 -> kubernetes/ (labs 09-12)
#   .\scripts\k8s-up.ps1 gitops dev      -> gitops/overlays/dev (lab 16)
param([string]$Source = "raw", [string]$Overlay = "dev")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$clusters = kind get clusters 2>$null
if ($clusters -notcontains "techpix") {
    Write-Host "== criando cluster kind 'techpix'"
    kind create cluster --config kubernetes/kind-config.yaml --wait 60s
} else {
    Write-Host "== cluster kind 'techpix' ja existe"
}
kubectl config use-context kind-techpix | Out-Null

Write-Host "== construindo imagens"
docker compose --profile app build -q monolith fraud-service

Write-Host "== carregando imagens no cluster"
kind load docker-image techpix/monolith:local techpix/fraud-service:local --name techpix

Write-Host "== metrics-server"
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml | Out-Null
kubectl -n kube-system patch deployment metrics-server --type=json -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]' 2>$null | Out-Null

Write-Host "== aplicando manifests ($Source)"
if ($Source -eq "raw") { kubectl apply -k kubernetes/ } else { kubectl apply -k "gitops/overlays/$Overlay" }

kubectl -n techpix rollout status deployment/postgres --timeout=120s
kubectl -n techpix rollout status deployment/monolith --timeout=300s
kubectl -n techpix rollout status deployment/fraud-service --timeout=300s
kubectl -n techpix get pods -o wide
Write-Host ""
Write-Host "Monolito:      http://localhost:8090"
Write-Host "Fraud Service: http://localhost:8091/actuator/health/readiness"
