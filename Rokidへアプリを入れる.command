#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$SCRIPT_DIR/Rokid-ZOOM-IN-CAMERA.apk"
PACKAGE="io.github.ksuzukigh.rokidzoomincamera"
TOOLS_DIR="$HOME/Library/Application Support/Rokid ZOOM IN CAMERA/platform-tools"
ADB="$TOOLS_DIR/adb"
DOWNLOAD_URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"

pause_and_exit() {
    echo
    read -r -p "Enterキーを押してください..." _ || true
    exit "${1:-1}"
}

prepare_adb() {
    if command -v adb >/dev/null 2>&1; then
        ADB="$(command -v adb)"
        return
    fi
    if [ -x "$ADB" ]; then
        return
    fi

    echo "Google公式のRokid接続ソフトを準備します..."
    local work_dir
    work_dir="$(mktemp -d)"
    trap 'if [ -n "${work_dir:-}" ] && [ -d "$work_dir" ]; then rm -rf "$work_dir"; fi' RETURN
    if ! curl --fail --location --silent --show-error "$DOWNLOAD_URL" -o "$work_dir/platform-tools.zip"; then
        echo "接続ソフトをダウンロードできませんでした。"
        pause_and_exit 1
    fi
    ditto -x -k "$work_dir/platform-tools.zip" "$work_dir/unpacked"
    mkdir -p "$(dirname "$TOOLS_DIR")"
    ditto "$work_dir/unpacked/platform-tools" "$TOOLS_DIR"
    if [ ! -x "$ADB" ]; then
        echo "接続ソフトを準備できませんでした。"
        pause_and_exit 1
    fi
}

find_rokid() {
    local serial model manufacturer
    while IFS= read -r serial; do
        [ -z "$serial" ] && continue
        model="$("$ADB" -s "$serial" shell getprop ro.product.model </dev/null 2>/dev/null | tr -d '\r' || true)"
        manufacturer="$("$ADB" -s "$serial" shell getprop ro.product.manufacturer </dev/null 2>/dev/null | tr -d '\r' || true)"
        if [ "$model" = "RG-glasses" ] && [ "$manufacturer" = "Rokid" ]; then
            printf '%s' "$serial"
            return 0
        fi
    done < <("$ADB" devices </dev/null | awk 'NR > 1 && $2 == "device" { print $1 }')
    return 1
}

if [ ! -f "$APK" ]; then
    echo "Rokid-ZOOM-IN-CAMERA.apkが見つかりません。ZIPを展開したフォルダを確認してください。"
    pause_and_exit 1
fi

prepare_adb

echo "Rokidを開発用5ピンケーブルでMacへつないでください。"
echo "接続を最大60秒待ちます..."

SERIAL=""
for _ in $(seq 1 60); do
    SERIAL="$(find_rokid || true)"
    [ -n "$SERIAL" ] && break
    if "$ADB" devices </dev/null | awk 'NR > 1 && $2 == "unauthorized" { found=1 } END { exit !found }'; then
        echo "Rokidに確認画面が出たら、USB接続を許可してください。"
    fi
    sleep 1
done

if [ -z "$SERIAL" ]; then
    echo "Rokidを確認できませんでした。"
    echo "Rokidのスマホアプリ側で開発者モード（ADB）が有効か、"
    echo "充電用ではなく開発用5ピンケーブルかを確認してください。"
    pause_and_exit 1
fi

echo "Rokid ZOOM IN CAMERAをインストールしています..."
if ! "$ADB" -s "$SERIAL" install -r "$APK" </dev/null; then
    echo
    echo "インストールできませんでした。"
    echo "別の署名で作られた試作版が入っている場合は、Rokidアプリの"
    echo "「メガネのアプリ管理」から旧版を削除し、もう一度実行してください。"
    pause_and_exit 1
fi

"$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE" </dev/null
"$ADB" -s "$SERIAL" shell am start -n "$PACKAGE/.MainActivity" </dev/null

echo
echo "インストールが完了しました。"
echo "Rokidで『Rokid ZOOM IN CAMERA』を開いてください。"
pause_and_exit 0
