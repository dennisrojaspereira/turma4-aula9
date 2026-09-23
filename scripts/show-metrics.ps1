# Mostra as metricas que respondem "onde o tempo esta sendo gasto?" (equivalente a show-metrics.sh).
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }
$m = "$base/actuator/metrics"

function Get-Stat($metric, $mode, $tag) {
    $url = "$m/$metric"
    if ($tag) { $url = "$url?tag=$tag" }
    try {
        $d = Invoke-RestMethod -Uri $url
        $s = @{}
        foreach ($x in $d.measurements) { $s[$x.statistic] = $x.value }
        $factor = if ($d.baseUnit -eq "seconds") { 1000 } else { 1 }
        switch ($mode) {
            "count"   { return [int]$s["COUNT"] }
            "value"   { return [math]::Round($s["VALUE"], 2) }
            "max_ms"  { return [math]::Round($s["MAX"] * $factor, 1) }
            "mean_ms" {
                if ($s["COUNT"] -eq 0) { return "n/a" }
                $total = if ($s.ContainsKey("TOTAL_TIME")) { $s["TOTAL_TIME"] } else { $s["TOTAL"] }
                return [math]::Round($total / $s["COUNT"] * $factor, 1)
            }
            "mean"    { if ($s["COUNT"] -eq 0) { return "n/a" }; return [math]::Round($s["TOTAL"] / $s["COUNT"], 1) }
        }
    } catch { return "n/a" }
}

Write-Host "== Perfil de Fraud"
Invoke-RestMethod -Uri "$base/admin/fraud/profile" | ConvertTo-Json -Compress
Write-Host ""
Write-Host "== Payment (POST /payments)"
Write-Host "  requests:        $(Get-Stat 'payment.create' 'count')"
Write-Host "  mean ms:         $(Get-Stat 'payment.create' 'mean_ms')"
Write-Host "  max ms:          $(Get-Stat 'payment.create' 'max_ms')"
Write-Host "  queries/payment: $(Get-Stat 'payment.queries' 'mean')"
Write-Host ""
Write-Host "== Fraud (FraudService.evaluate)"
Write-Host "  mean ms:         $(Get-Stat 'fraud.evaluation' 'mean_ms')"
Write-Host "  max ms:          $(Get-Stat 'fraud.evaluation' 'max_ms')"
Write-Host "  queries/fraud:   $(Get-Stat 'fraud.queries' 'mean')"
Write-Host ""
Write-Host "== Pool de conexoes (HikariCP)"
Write-Host "  max:             $(Get-Stat 'hikaricp.connections.max' 'value')"
Write-Host "  active:          $(Get-Stat 'hikaricp.connections.active' 'value')"
Write-Host "  pending:         $(Get-Stat 'hikaricp.connections.pending' 'value')   <- threads esperando conexao agora"
Write-Host "  acquire mean ms: $(Get-Stat 'hikaricp.connections.acquire' 'mean_ms')   <- quanto se espera por uma conexao"
Write-Host "  usage mean ms:   $(Get-Stat 'hikaricp.connections.usage' 'mean_ms')   <- quanto tempo cada conexao fica presa"
Write-Host ""
Write-Host "== Tempo medio por regra de Fraud (ms) e consultas por regra"
try {
    $rules = ((Invoke-RestMethod -Uri "$m/fraud.rule").availableTags | Where-Object { $_.tag -eq 'rule' }).values
    $rows = foreach ($r in $rules) {
        [pscustomobject]@{ rule = $r; ms = Get-Stat 'fraud.rule' 'mean_ms' "rule:$r"; queries = Get-Stat 'fraud.rule.queries' 'mean' "rule:$r" }
    }
    $rows | Sort-Object ms -Descending | Format-Table -AutoSize
} catch { Write-Host "  (nenhuma avaliacao de fraude ainda)" }
