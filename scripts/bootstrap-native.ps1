$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Tag = if ($env:XRAY_AAR_TAG) { $env:XRAY_AAR_TAG } else { "v26.6.27" }
$Out = Join-Path $Root "app\libs\libv2ray.aar"
$Temp = "$Out.tmp"
$Primary = "https://github.com/2dust/AndroidLibXrayLite/releases/download/$Tag/libv2ray.aar"
$Version = $Tag.TrimStart('v')
$Fallback = "https://sourceforge.net/projects/androidlibxraylite.mirror/files/$Version/libv2ray.aar/download"
New-Item -ItemType Directory -Force (Split-Path -Parent $Out) | Out-Null
Remove-Item -Force -ErrorAction SilentlyContinue $Temp
Write-Host "Downloading AndroidLibXrayLite $Tag..."
try {
    Invoke-WebRequest -UseBasicParsing -Uri $Primary -OutFile $Temp
} catch {
    Write-Warning "GitHub download failed; trying SourceForge mirror."
    Invoke-WebRequest -UseBasicParsing -Uri $Fallback -OutFile $Temp
}
if ((Get-Item $Temp).Length -lt 10000000) { throw "Downloaded AAR is unexpectedly small" }
$Stream = [System.IO.File]::OpenRead($Temp)
try {
    $First = $Stream.ReadByte()
    $Second = $Stream.ReadByte()
} finally {
    $Stream.Dispose()
}
if ($First -ne 0x50 -or $Second -ne 0x4B) { throw "Downloaded file is not an AAR/ZIP" }
Move-Item -Force $Temp $Out
(Get-FileHash -Algorithm SHA256 $Out).Hash.ToLower() | Set-Content "$Out.sha256"
Write-Host "Installed $Out"
