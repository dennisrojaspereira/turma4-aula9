# Instala o Argo CD no cluster kind "techpix" e registra as Applications (equivalente a argocd-up.sh).
#   $env:TECHPIX_GIT_URL = "https://github.com/SEU-USUARIO/tech-pix.git"; .\scripts\argocd-up.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if (-not $env:TECHPIX_GIT_URL) { throw "defina TECHPIX_GIT_URL com a URL do seu repositorio Git" }

kubectl config use-context kind-techpix | Out-Null
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f - | Out-Null
kubectl apply --server-side --force-conflicts -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml | Out-Null
kubectl -n argocd rollout status deployment/argocd-server --timeout=600s
kubectl -n argocd rollout status deployment/argocd-repo-server --timeout=600s
kubectl -n argocd rollout status statefulset/argocd-application-controller --timeout=600s

foreach ($envName in @("dev", "qa", "prod")) {
    (Get-Content "argocd/applications/$envName.yaml" -Raw).Replace('${TECHPIX_GIT_URL}', $env:TECHPIX_GIT_URL) | kubectl apply -f -
}
$pass = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String((kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}')))
Write-Host ""
Write-Host "Argo CD instalado."
Write-Host "  UI:      kubectl -n argocd port-forward svc/argocd-server 8443:443  ->  https://localhost:8443"
Write-Host "  usuario: admin"
Write-Host "  senha:   $pass"
