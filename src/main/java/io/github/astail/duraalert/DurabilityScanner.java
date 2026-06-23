package io.github.astail.duraalert;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * プレイヤーの装備・手持ち・インベントリを走査し、
 * 残り耐久値が閾値（%）を下回った破壊可能アイテムを抽出する純粋ロジック。状態は持たない。
 */
public final class DurabilityScanner {

    private DurabilityScanner() {
    }

    /**
     * 装備（防具4・両手）と、必要ならインベントリ内を走査して、閾値未満のアイテムを返す。
     *
     * @param thresholdPercent この割合(%)未満を「低耐久」とみなす
     * @param checkInventory   true ならインベントリ内（手持ち以外）も対象にする
     */
    public static List<LowDurabilityItem> scan(Player player, double thresholdPercent, boolean checkInventory) {
        PlayerInventory inv = player.getInventory();
        List<LowDurabilityItem> found = new ArrayList<>();
        int heldSlot = inv.getHeldItemSlot();

        // 装備スロット（常にチェック）。メインハンドもここで見る。
        addIfLow(found, inv.getItem(EquipmentSlot.HEAD), "ヘルメット", thresholdPercent);
        addIfLow(found, inv.getItem(EquipmentSlot.CHEST), "チェストプレート", thresholdPercent);
        addIfLow(found, inv.getItem(EquipmentSlot.LEGS), "レギンス", thresholdPercent);
        addIfLow(found, inv.getItem(EquipmentSlot.FEET), "ブーツ", thresholdPercent);
        // メインハンドは装備スロット(EquipmentSlot.HAND)で見る。重複抑止キーはスロット非依存
        // （アイテム種別＋表示名）なので、選択スロットを切り替えても・別スロットへ移しても再通知されない。
        addIfLow(found, inv.getItem(EquipmentSlot.HAND), "メインハンド", thresholdPercent);
        addIfLow(found, inv.getItem(EquipmentSlot.OFF_HAND), "オフハンド", thresholdPercent);

        // インベントリ内（任意）。メインハンド（= heldSlot）は上で見ているので、/check の二重表示を
        // 防ぐためスキップする。
        if (checkInventory) {
            ItemStack[] storage = inv.getStorageContents();
            for (int i = 0; i < storage.length; i++) {
                if (i == heldSlot) {
                    continue;
                }
                addIfLow(found, storage[i], "インベントリ#" + i, thresholdPercent);
            }
        }
        return found;
    }

    /** アイテムが破壊可能で閾値未満なら found に追加する。 */
    private static void addIfLow(List<LowDurabilityItem> out, ItemStack item, String slotLabel,
                                 double thresholdPercent) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.isUnbreakable()) {
            return;
        }
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        int remaining = Math.max(0, max - damageable.getDamage());
        double percent = (double) remaining * 100.0 / max;
        if (percent >= thresholdPercent) {
            return;
        }
        Component name = item.effectiveName();
        // 重複抑止キーはスロット非依存（アイテム種別 + 表示名）。これにより、同じアイテムを
        // 手持ち⇄インベントリ⇄装備と移動してもキーが変わらず、一度通知したアイテムは
        // 閾値を回復／消失するまで再通知されない。
        String key = item.getType().name() + "#"
                + PlainTextComponentSerializer.plainText().serialize(name);
        out.add(new LowDurabilityItem(key, slotLabel, name, remaining, max, percent));
    }
}
