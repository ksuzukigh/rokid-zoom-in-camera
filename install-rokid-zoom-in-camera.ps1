$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Apk = Join-Path $ScriptDir "Rokid-ZOOM-IN-CAMERA.apk"
$Package = "io.github.ksuzukigh.rokidzoomincamera"
$ToolsDir = Join-Path $env:LOCALAPPDATA "Rokid ZOOM IN CAMERA\platform-tools"
$Adb = Join-Path $ToolsDir "adb.exe"
$DownloadUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

function Stop-WithMessage([string]$Message) {
    Write-Host ""
    Write-Host $Message
    exit 1
}

if (-not (Test-Path $Apk)) {
    Stop-WithMessage "Rokid-ZOOM-IN-CAMERA.apkが見つかりません。ZIPを展開したフォルダを確認してください。"
}

$ExistingAdb = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($ExistingAdb) {
    $Adb = $ExistingAdb.Source
} elseif (-not (Test-Path $Adb)) {
    Write-Host "Google公式のRokid接続ソフトを準備します..."
    $WorkDir = Join-Path ([System.IO.Path]::GetTempPath()) ("rokid-zoom-in-" + [Guid]::NewGuid().ToString("N"))
    try {
        New-Item -ItemType Directory -Path $WorkDir | Out-Null
        $Zip = Join-Path $WorkDir "platform-tools.zip"
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $Zip -UseBasicParsing
        Expand-Archive -Path $Zip -DestinationPath $WorkDir -Force
        New-Item -ItemType Directory -Path $ToolsDir -Force | Out-Null
        Copy-Item -Path (Join-Path $WorkDir "platform-tools\*") -Destination $ToolsDir -Recurse -Force
    } finally {
        if (Test-Path $WorkDir) { Remove-Item -Path $WorkDir -Recurse -Force }
    }
}

if (-not (Test-Path $Adb)) {
    Stop-WithMessage "接続ソフトを準備できませんでした。"
}

function Find-Rokid {
    $Lines = & $Adb devices
    foreach ($Line in $Lines) {
        if ($Line -match '^([^\s]+)\s+device$') {
            $Serial = $Matches[1]
            $Model = ((& $Adb -s $Serial shell getprop ro.product.model) -join "").Trim()
            $Manufacturer = ((& $Adb -s $Serial shell getprop ro.product.manufacturer) -join "").Trim()
            if ($Model -eq "RG-glasses" -and $Manufacturer -eq "Rokid") {
                return $Serial
            }
        }
    }
    return $null
}

Write-Host "Rokidを開発用5ピンケーブルでWindows PCへつないでください。"
Write-Host "接続を最大60秒待ちます..."

$Serial = $null
for ($Attempt = 0; $Attempt -lt 60; $Attempt++) {
    $Serial = Find-Rokid
    if ($Serial) { break }
    $Devices = (& $Adb devices) -join "`n"
    if ($Devices -match '\sunauthorized\s*$') {
        Write-Host "Rokidに確認画面が出たら、USB接続を許可してください。"
    }
    Start-Sleep -Seconds 1
}

if (-not $Serial) {
    Stop-WithMessage "Rokidを確認できませんでした。Rokidアプリ側の開発者モード（ADB）と、開発用5ピンケーブルを確認してください。"
}

Write-Host "Rokid ZOOM IN CAMERAをインストールしています..."
& $Adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0) {
    Stop-WithMessage "インストールできませんでした。別の署名の試作版がある場合は、Rokidアプリの「メガネのアプリ管理」から旧版を削除してください。"
}

& $Adb -s $Serial shell am force-stop $Package | Out-Null
& $Adb -s $Serial shell am start -n "$Package/.MainActivity" | Out-Null

Write-Host ""
Write-Host "インストールが完了しました。"
Write-Host "Rokidで『Rokid ZOOM IN CAMERA』を開いてください。"
