package io.github.astail.duraalert;

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
        addIfLow(found, inv.getItem(EquipmentSlot.HEAD), "ヘルメット", "EQ:HEAD", thresholdPercent);
        addIfLow(found, inv.getItem(EquipmentSlot.CHEST), "チェストプレート", "EQ:CHEST", thresholdPercent);
        addIfLow(found, inv.getItem(EquipmentSlot.LEGS), "レギンス", "EQ:LEGS", thresholdPercent);
        addIfLow(found, inv.getItem(EquipmentSlot.FEET), "ブーツ", "EQ:FEET", thresholdPercent);
        // メインハンドはホットバーの「現在選択中スロット」そのもの。装備キー(EQ:HAND)にすると
        // 選択を切り替えるたびに EQ:HAND ⇄ INV:n とキーが変わり再通知されてしまうため、
        // ホットバーのスロット番号(INV:heldSlot)でキー付けして選択状態に依存しないようにする。
        addIfLow(found, inv.getItem(EquipmentSlot.HAND), "メインハンド", "INV:" + heldSlot, thresholdPercent);
        addIfLow(found, inv.getItem(EquipmentSlot.OFF_HAND), "オフハンド", "EQ:OFF_HAND", thresholdPercent);

        // インベントリ内（任意）。メインハンド（= heldSlot）は上で見ているのでスキップして二重計上を防ぐ。
        if (checkInventory) {
            ItemStack[] storage = inv.getStorageContents();
            for (int i = 0; i < storage.length; i++) {
                if (i == heldSlot) {
                    continue;
                }
                addIfLow(found, storage[i], "インベントリ#" + i, "INV:" + i, thresholdPercent);
            }
        }
        return found;
    }

    /** アイテムが破壊可能で閾値未満なら found に追加する。 */
    private static void addIfLow(List<LowDurabilityItem> out, ItemStack item, String slotLabel,
                                 String slotKey, double thresholdPercent) {
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
        out.add(new LowDurabilityItem(
                slotKey + "#" + item.getType().name(),
                slotLabel,
                item.effectiveName(),
                remaining,
                max,
                percent));
    }
}
