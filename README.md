# Rokid ZOOM IN CAMERA

Rokid AI Glasses RV101の広角カメラを、見たい範囲へ寄せて使うズームカメラです。

**現在のバージョン: 1.0.0**

[Rokid ZOOM IN CAMERAの最新版をダウンロード](https://github.com/ksuzukigh/rokid-zoom-in-camera/releases/latest/download/Rokid-ZOOM-IN-CAMERA.zip)

## できること

- カメラ映像を1.0 / 1.5 / 2.0 / 3.0 / 4.0倍で表示
- テンプルのボタンで写真撮影
- 音声の入らない動画撮影
- 眼鏡本体の電池残量をリアルタイム表示
- 録画中の経過時間を画面右上に表示
- 写真と動画をRokidの標準カメラフォルダへ保存
- 通信、クラウド、位置情報、マイクを使用しない

## 操作方法

| したいこと | 操作 |
|---|---|
| 倍率を変える | 左右へスワイプ |
| 写真を撮る | ボタンを1回押す |
| 動画を撮り始める | ボタンを長押しする。指を離しても録画は続く |
| 動画を終了する | 録画中にボタンを1回押す |

録画中は倍率を変えられません。録画を始める前に倍率を選んでください。

電池残量は画面右上に常時表示されます。残量が20%以下では「電池少」、10%以下では「要充電」と文字で知らせます。

## 保存先

写真と動画はRokid本体の`DCIM/Camera`へ保存されます。ファイル名に撮影日時と倍率が入ります。

- 写真の例: `RokidZoomIn_20260816_123456_2.0x.jpg`
- 動画の例: `RokidZoomIn_20260816_123456_2.0x.mp4`

Hi Rokidアプリのメディア一覧には、動画が表示されない場合があります。その場合も動画ファイル自体はRokid本体へ保存されています。

## 用意するもの

- Rokid AI Glasses RV101
- 最初のインストールに使うMacまたはWindows PC
- Rokidの開発用5ピンケーブル
- スマートフォンのRokidアプリ側で、開発者モード（ADB）を有効にしておくこと

開発用5ピンケーブルをつなぐだけではADBは有効になりません。先にRokidアプリ側の設定を有効にしてください。

## インストール

1. [最新版ZIP](https://github.com/ksuzukigh/rokid-zoom-in-camera/releases/latest/download/Rokid-ZOOM-IN-CAMERA.zip)をダウンロードし、展開します。
2. Rokidを開発用5ピンケーブルでMacまたはWindows PCへつなぎます。
3. RokidにUSB接続の確認が出た場合は許可します。
4. Macは`「Rokidへアプリを入れる.command」`、Windowsは`install-rokid-zoom-in-camera.cmd`を開きます。
5. 画面の案内に従います。

必要な接続ソフトがない場合は、Google公式のAndroid Platform-Toolsを自動で準備します。HomebrewやAndroid Studioの事前導入は不要です。

<details>
<summary>Macに止められた場合</summary>

警告画面で「完了」を押した後、Macの「システム設定」→「プライバシーとセキュリティ」の順に開き、該当ファイルの「このまま開く」を選びます。

</details>

## 注意

- ズームはカメラ映像の中央を切り出すデジタルズームです。倍率を上げるほど画像は粗くなります。
- 動画は約1分75MBを目安に保存容量を使います。
- 長時間の録画は電池を大きく消費し、本体が熱くなる場合があります。
- アプリ使用中はRokid標準カメラとの衝突を防ぐため、カメラボタンの標準動作を一時的に停止します。アプリの終了、異常停止、再起動時に標準設定へ戻します。
- Rokid AI Glasses RV101 / YodaOS専用です。

## プライバシー

インターネット通信、マイク、位置情報、連絡先、利用状況の収集を行いません。詳細は[プライバシー](PRIVACY.md)を参照してください。

## 削除するには

スマートフォンのRokidアプリで「ホーム」→「ツールボックス」→「メガネのアプリ管理」を開き、「Rokid ZOOM IN CAMERA」を削除します。保存済みの写真と動画は消えません。

## 独立実装について

本プロジェクトはRV101実機とYodaOSの公開・観測可能な動作を調査し、一から実装しています。第三者の未ライセンスのソースコードや素材は使用していません。

## 開発者向け

JDK 17とAndroid SDKが必要です。

```bash
./gradlew test lint assembleDebug
```

## ライセンス

MIT License（詳細は[LICENSE](LICENSE)）
