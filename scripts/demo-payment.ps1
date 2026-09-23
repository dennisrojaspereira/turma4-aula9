# Abre duas contas e faz um pagamento (equivalente a demo-payment.sh).
$base = if ($env:TECHPIX_URL) { $env:TECHPIX_URL } else { "http://localhost:8080" }

$alice = (Invoke-RestMethod -Method Post -Uri "$base/accounts" -ContentType "application/json" `
    -Body '{"ownerName":"Alice","initialBalance":1000}').id
$bob = (Invoke-RestMethod -Method Post -Uri "$base/accounts" -ContentType "application/json" `
    -Body '{"ownerName":"Bob","initialBalance":0}').id

Write-Host "Alice: $alice"
Write-Host "Bob:   $bob"
Write-Host ""
Write-Host "POST /payments"
Invoke-RestMethod -Method Post -Uri "$base/payments" -ContentType "application/json" `
    -Body (@{ payerAccountId = $alice; payeeAccountId = $bob; amount = 250; deviceId = "demo-device" } | ConvertTo-Json) | ConvertTo-Json
Write-Host ""
Write-Host "Saldo de Alice:"
Invoke-RestMethod -Uri "$base/accounts/$alice" | ConvertTo-Json
