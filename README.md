# DuraAlert

持っている**アイテムや装備**の**残り耐久値が一定割合を下回ったら、本人のチャットへ通知する**プラグインです（Paper 用 / サーバー側のみ）。

「気づいたら大事な道具が壊れていた」を防ぎます。装備・両手・インベントリ内のアイテムを一定間隔で監視し、残り耐久値が **既定 10%** を切ったアイテムをその場で知らせます。

> **クライアント MOD は不要です。** サーバーにこのプラグインを入れるだけで、バニラのクライアントでもそのまま通知が届きます。

## 解決する課題

「ダイヤのツルハシやエリトラ、ネザライト装備が、使っているうちに気づかぬまま壊れてしまう」。
DuraAlert は耐久値を見張り、**危なくなったら（既定 10% 未満）チャットとベル音で警告**します。

## 主な機能

- **耐久値の自動監視**: 一定間隔（既定 5 秒）で、装備（防具・両手）と手持ち・インベントリ内のアイテムをチェック。
- **閾値で通知**: 残り耐久値が設定した割合（既定 10%）を下回ると、対象アイテムを本人のチャットへ通知。ベル音も鳴らせます。
- **重複通知を抑止**: 一度通知したアイテムは、修理などで回復するまで繰り返し通知しません（うるさくならない）。
- **対象範囲を選べる**: 装備＋両手だけにするか、インベントリ内のアイテムまで見るかを設定できます。
- **手動チェック**: `/duraalert check` でいつでも「今あぶないアイテム」を一覧表示。
- **ミュート / 全体 ON-OFF**: 自分だけ通知を止める `mute`、サーバー全体を止める `on|off`。

## 動作要件

- サーバー: Paper 26.1.2（build 69 以上）
- Java: 25
- クライアント: バニラで可（MOD 不要・サーバー側のみ）

## 導入

1. `DuraAlert-1.0.1.jar` を `plugins/` に置いてサーバーを再起動します。
2. 以上で監視が始まります。耐久値が 10% を下回ったアイテムがあると、自動でチャットに通知されます。

例:

```text
[DuraAlert] ダイヤモンドの剣 (メインハンド) の耐久値が残り 8%（124/1561）です！
```

## 使い方

設定はデフォルトのままでも動作します。挙動を変えたいときは `config.yml`（後述）か、ゲーム内コマンドで調整します。

- 今あぶないアイテムを確認したい → `/duraalert check`
- しばらく通知を止めたい（自分だけ） → `/duraalert mute` ／ 戻す → `/duraalert unmute`
- 現在の設定を見たい → `/duraalert status`

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/duraalert check` | 今、耐久値が閾値を下回っているアイテムを一覧表示 | `duraalert.use` |
| `/duraalert status` | 現在の設定（閾値・間隔など）と自分のミュート状態を表示 | `duraalert.use` |
| `/duraalert mute \| unmute` | 自分への通知を停止 / 再開（再起動でリセット） | `duraalert.use` |
| `/duraalert on \| off` | 監視・通知の全体 有効 / 無効 | `duraalert.manage` |
| `/duraalert reload` | 設定を再読み込み | `duraalert.manage` |

エイリアス: `/da`

## 権限

| 権限ノード | 説明 | 既定 |
|---|---|---|
| `duraalert.notify` | 耐久値が下がったときに通知を受け取る | `true`（全員） |
| `duraalert.use` | `check` / `status` / `mute` などの操作 | `true`（全員） |
| `duraalert.manage` | `on` / `off` / `reload` などサーバー全体に影響する操作 | `op` |

## 設定（`config.yml`）

```yaml
enabled: true            # 監視・通知を行うか（/duraalert on|off で切替）
threshold-percent: 10    # 残り耐久値がこの割合(%)を下回ったら通知（1〜100）
check-interval-ticks: 100 # 何 tick ごとにチェックするか（20 tick = 1 秒・最小 20）
notify-sound: true       # 通知時にベル音を鳴らすか
check-inventory: true    # インベントリ内のアイテムもチェックするか（false なら装備と両手のみ）
```

## 仕組み / 技術メモ

- 一定間隔のスケジューラ（`runTaskTimer`）で各プレイヤーのアイテムを走査します。対象は防具4スロット・メインハンド・オフハンドと、`check-inventory: true` ならインベントリ内（手持ちスロットの重複を除く）。
- 各アイテムの最大耐久値は data component（`getMaxDamage`）優先、無ければバニラの最大値を使い、`残り / 最大 × 100` が閾値未満なら通知対象とします。`Unbreakable`（破壊不能）や耐久値を持たないアイテムは対象外です。
- 通知は「閾値を下回った瞬間」に 1 回だけ。プレイヤーごとに通知済みのアイテムを記憶し、修理などで閾値以上に戻る（または手放す）まで再通知しません。
- 表示名は `ItemStack#effectiveName()` を使うため、金床で付けたカスタム名もそのまま表示されます。

### 制限

- 監視はポーリング方式です。`check-interval-ticks` を短くすると反応は速くなりますが、サーバー負荷が増えます（最小 1 秒）。
- ミュート状態は永続化しません（サーバー再起動でリセット）。

## ビルド

```bash
./deploy.sh        # Mac ネイティブ（JDK 25 + Maven）。生成物: target/DuraAlert-1.0.1.jar
# または
mvn -B clean package
```

`v*` タグを push すると GitHub Actions（`.github/workflows/build.yml`）がビルドし、リリースに jar を添付します。

## サーバーへの配置

サーバーの `plugins/` に jar を置いてサーバーを再起動します。jar の入手は次の 2 通り（A・B）です。Docker（itzg/minecraft-server）を使う場合は、後述の「Docker Compose で自動ダウンロード」も利用できます。

### A. リリース版を使う（ビルド不要・推奨）

[Releases](https://github.com/astail/mc-check-durability/releases) から最新の `DuraAlert-<version>.jar` をダウンロードします。JDK や Maven は不要です。

```bash
# 最新リリースの jar をダウンロード（gh CLI を使う場合）
gh release download --repo astail/mc-check-durability --pattern '*.jar'
```

### B. 自分でビルドする

[ビルド](#ビルド) の手順で `target/DuraAlert-1.0.1.jar` を生成します。

### 配置

入手した jar をサーバーの `plugins/` に置いてサーバーを再起動します。

```bash
# バインドマウントしている場合（ホスト側 plugins ディレクトリへコピー）
cp target/DuraAlert-1.0.1.jar /path/to/data/plugins/
docker restart <コンテナ名>

# 名前付きボリューム等の場合（コンテナへ直接コピー）
docker cp target/DuraAlert-1.0.1.jar <コンテナ名>:/data/plugins/
docker restart <コンテナ名>
```

### Docker Compose（itzg/minecraft-server）で自動ダウンロード

[`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server) イメージを使う場合は、jar を手元に用意しなくても **`PLUGINS` 環境変数にリリースの URL を並べるだけ**で、起動時に自動ダウンロードして `plugins/` に配置できます。

```yaml
services:
  mc:
    image: itzg/minecraft-server
    tty: true
    stdin_open: true
    ports:
      - "25565:25565"
    environment:
      EULA: "TRUE"
      TYPE: "PAPER"
      VERSION: "26.2"
      PAPER_CHANNEL: "experimental"
      PLUGINS: |
        https://github.com/astail/mc-check-durability/releases/download/v1.0.1/DuraAlert-1.0.1.jar
    volumes:
      - ./data:/data
    restart: unless-stopped
```

`PLUGINS` は改行区切りで複数指定できます。バージョンを更新したら、URL の `v1.0.1` とファイル名を新しいリリースに合わせて変更してください（例: `.../download/v1.0.1/DuraAlert-1.0.1.jar`）。

起動ログに以下が出れば成功です。

```text
[DuraAlert] DuraAlert を有効化しました（閾値: 10% / 状態: ON）。
```

## ライセンス

MIT License — [LICENSE](LICENSE) を参照。
