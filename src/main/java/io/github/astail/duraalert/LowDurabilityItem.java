package io.github.astail.duraalert;

import net.kyori.adventure.text.Component;

/**
 * 耐久値が閾値を下回ったアイテム 1 件分の情報。
 *
 * @param key       通知の重複抑止に使う安定キー。アイテム個体の固有 ID（{@code UID:<uuid>}）で、
 *                  スロット非依存。固有 ID 未付与の読み取り専用経路では {@code NOID:<種別>#<表示名>} を暫定使用
 * @param slotLabel 表示用のスロット名（例: メインハンド / ヘルメット / インベントリ#3）
 * @param name      アイテムの表示名（カスタム名 or バニラ名）
 * @param remaining 残り耐久値
 * @param max       最大耐久値
 * @param percent   残り耐久値の割合（%）
 */
public record LowDurabilityItem(
        String key,
        String slotLabel,
        Component name,
        int remaining,
        int max,
        double percent) {

    /** 表示用に切り捨てた整数パーセント。 */
    public int displayPercent() {
        return (int) Math.floor(percent);
    }
}
