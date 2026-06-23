# CLAUDE.md

Claude がこのリポジトリで作業する際の開発メモ（Paper プラグイン）。

## プラグインの目的

DuraAlert は、各プレイヤーの装備・手持ち・インベントリ内アイテムを一定間隔で監視し、残り耐久値が閾値（既定 10%）を下回ったアイテムを **本人のチャットへ通知** する。サーバー側のみで動き、クライアント MOD は不要。

## ビルド要件

- Java 25 + Maven。生成物は `DuraAlert-1.0.1.jar`。
- 唯一の依存は `io.papermc.paper:paper-api:26.1.2.build.69-stable`（provided）。
- ローカルビルドは `./deploy.sh`（Homebrew `openjdk@25` を想定）。

## アーキテクチャ構成

- **DuraAlertPlugin**: 本体。設定読込、`runTaskTimer` による定期スキャン、通知の組み立て・送信、通知済み状態（`warned`）とミュート（`muted`）の管理、`PlayerQuitEvent` での状態破棄。
- **DuraAlertCommand**: `/duraalert <check|status|mute|unmute|on|off|reload>` の実処理とタブ補完。
- **DurabilityScanner**: プレイヤーの装備（防具4・両手）と、必要ならインベントリ内を走査し、閾値未満の破壊可能アイテムを `LowDurabilityItem` のリストで返す。定期スキャン時（`assignIdentity=true`）は、低耐久アイテムへ個体識別用の固有 ID（`PersistentDataContainer`）を一度だけ刻む副作用を持つ。`/check`（`assignIdentity=false`）は書き込まない。
- **LowDurabilityItem**: 低耐久アイテム 1 件分（重複抑止キー・スロット名・表示名・残り/最大/割合）の record。

## 設計上の要点

- **重複通知の抑止は「状態遷移」方式**: プレイヤーごとに通知済みキー集合（`warned`）を持ち、新たに閾値を下回ったアイテムだけ通知する。毎スキャンの最後に `retainAll(現在の低耐久キー)` で、回復・消失したアイテムを集合から外す → 次に下回ったら再通知できる。クールダウンではなくヒステリシス的に動く。
- **キーはアイテム固有 ID（スロット非依存・個体ごと）**: 低耐久を初検知した瞬間に各アイテムへ `duraalert:uid`（ランダム UUID）を `PersistentDataContainer` で 1 回だけ刻み、キーを `UID:<uuid>` とする。UID はアイテムと共に永続化されスロット移動に追従するので、手持ち⇄インベントリ⇄装備と移動しても再通知されない。さらに**同一種別・同一表示名のアイテムでも個体ごとに区別**できる。耐久アイテムはスタック数 1 なので「別 NBT でスタックしなくなる」問題は実質発生しない。書き込みは初回低耐久時の 1 回だけ（以降は読むだけ）。回復・消失すれば `warned` から外れ、再び下回れば再通知できる（UID 自体はアイテムに残る）。`/check`（`assignIdentity=false`）は書き込まず、未付与アイテムは種別＋表示名の暫定キー（重複抑止に未使用）で代用。
- **メインハンドの二重計上を回避**: 装備スロット（`EquipmentSlot.HAND`）で見るため、インベントリ走査では `getHeldItemSlot()` をスキップする。防具・オフハンドは `getStorageContents()` に含まれないので重複しない。
- **最大耐久値**: `Damageable#hasMaxDamage()` なら `getMaxDamage()`（data component の上書きに追従）、無ければ `Material#getMaxDurability()` を使う。`isUnbreakable()` や max ≤ 0 のアイテムは対象外。
- **表示名は `ItemStack#effectiveName()`**（カスタム名 or バニラ名の Component）。色が無ければ `colorIfAbsent(GOLD)` で着色。通知音は Adventure の `Sound`（`block.note_block.bell`）。
- **権限は 2 段階**: `check`・`status`・`mute`/`unmute` は `duraalert.use`（既定 true）。サーバー全体に影響する `on`・`off`・`reload` は `duraalert.manage`（既定 op）を別途要求（`DuraAlertCommand#requireManage`）。通知の受信可否は `duraalert.notify`（既定 true）。
- **ミュートは非永続**（メモリ上の `Set<UUID>`）。サーバー再起動でリセットされる。`/duraalert reload` は config と監視タスクを読み直し、`warned` をクリアする（ミュートは維持）。

## 既知の制限 / 注意

- スキャンは `check-interval-ticks`（既定 100 tick = 5 秒）ごとのポーリング。短くすると反応は速くなるが負荷が増える（最小 20 tick）。
- 通知は「閾値を下回った瞬間」に 1 回。さらに削れても回復するまでは再通知しない（仕様）。
- 固有 ID は使用中（弓を引く・食べる・盾構え等）のアイテムには初回スタンプを見送る（`getActiveItem()` 判定）。書き戻し（`setItem`）で使用アクションを中断しないため。そのアイテムの通知は次スキャンへ最大 1 周期ぶん遅れる。
- 通知が確実に届くのはチャット。`notify-sound` でベル音を併用できる。
- ミュート状態は永続化しない。永続化が必要なら別途プレイヤーデータの保存実装が要る。

## リリース手順

- セマンティックバージョニング。`v*` タグの push で GitHub Actions（`.github/workflows/build.yml`）がビルドし、`gh release create --generate-notes` で jar を添付する。
- サーバーへの配置（Releases から DL、または Docker `itzg/minecraft-server` の `PLUGINS` 環境変数で自動 DL）は README の「サーバーへの配置」を参照。
