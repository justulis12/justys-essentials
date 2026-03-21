param(
    [string]$ModsDir = "."
)

$modsPath = Resolve-Path -Path $ModsDir
$pendingJars = Get-ChildItem -Path $modsPath -Filter "*.jar.pending" -File -ErrorAction SilentlyContinue

if (-not $pendingJars) {
    Write-Host "No pending Justys' Essentials update found."
    exit 0
}

foreach ($pending in $pendingJars) {
    $targetName = [System.IO.Path]::GetFileNameWithoutExtension($pending.Name)
    $targetPath = Join-Path $modsPath $targetName
    $backupPath = "$targetPath.bak"
    $metadataPath = "$($pending.FullName).json"

    if (Test-Path $backupPath) {
        Remove-Item $backupPath -Force
    }

    if (Test-Path $targetPath) {
        Move-Item $targetPath $backupPath -Force
    }

    Move-Item $pending.FullName $targetPath -Force

    if (Test-Path $metadataPath) {
        Remove-Item $metadataPath -Force
    }

    Write-Host "Applied update to $targetPath"
}
