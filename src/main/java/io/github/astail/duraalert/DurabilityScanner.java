package io.github.astail.duraalert;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * プレイヤーの装備・手持ち・インベントリを走査し、
 * 残り耐久値が閾値（%）を下回った破壊可能アイテムを抽出するロジック。
 *
 * <p>定期スキャン時（assignIdentity=true）は、低耐久アイテムへ固有 ID を一度だけ刻む副作用を持つ
 * （個体識別のため。詳細は {@link #addIfLow} 参照）。
 */
public final class DurabilityScanner {

    private DurabilityScanner() {
    }

    /**
     * 装備（防具4・両手）と、必要ならインベントリ内を走査して、閾値未満のアイテムを返す。
     *
     * @param thresholdPercent この割合(%)未満を「低耐久」とみなす
     * @param checkInventory   true ならインベントリ内（手持ち以外）も対象にする
     * @param uidKey           個体識別用に固有 ID を保存する PersistentDataContainer のキー
     * @param assignIdentity   true なら固有 ID 未付与のアイテムへ新規 ID を刻む（定期スキャン用）。
     *                         false なら書き込まない（/check など読み取り専用の用途）。
     */
    public static List<LowDurabilityItem> scan(Player player, double thresholdPercent,
                                               boolean checkInventory, NamespacedKey uidKey,
                                               boolean assignIdentity) {
        PlayerInventory inv = player.getInventory();
        List<LowDurabilityItem> found = new ArrayList<>();
        int heldSlot = inv.getHeldItemSlot();
        // 使用中（弓を引く/食べる/盾構え等）の手（HAND か OFF_HAND、無ければ null）。固有 ID の書き戻しで
        // 使用アクションを中断しないよう、この手のアイテムだけは初回スタンプを見送る判定に使う。
        EquipmentSlot usingHand = player.hasActiveItem() ? player.getActiveItemHand() : null;

        // 装備スロット（常にチェック）。メインハンドもここで見る。
        addEquip(found, inv, EquipmentSlot.HEAD, "ヘルメット", thresholdPercent, uidKey, assignIdentity, usingHand);
        addEquip(found, inv, EquipmentSlot.CHEST, "チェストプレート", thresholdPercent, uidKey, assignIdentity, usingHand);
        addEquip(found, inv, EquipmentSlot.LEGS, "レギンス", thresholdPercent, uidKey, assignIdentity, usingHand);
        addEquip(found, inv, EquipmentSlot.FEET, "ブーツ", thresholdPercent, uidKey, assignIdentity, usingHand);
        addEquip(found, inv, EquipmentSlot.HAND, "メインハンド", thresholdPercent, uidKey, assignIdentity, usingHand);
        addEquip(found, inv, EquipmentSlot.OFF_HAND, "オフハンド", thresholdPercent, uidKey, assignIdentity, usingHand);

        // インベントリ内（任意）。メインハンド（= heldSlot）は上で見ているので、/check の二重表示を
        // 防ぐためスキップする。インベントリのアイテムは「使用中」になり得ないので itemInUse は常に false。
        if (checkInventory) {
            ItemStack[] storage = inv.getStorageContents();
            for (int i = 0; i < storage.length; i++) {
                if (i == heldSlot) {
                    continue;
                }
                final int slot = i;
                addIfLow(found, storage[i], "インベントリ#" + i, thresholdPercent, uidKey, assignIdentity,
                        it -> inv.setItem(slot, it), false);
            }
        }
        return found;
    }

    /**
     * 装備スロット 1 つを評価する。固有 ID を刻んだ場合は同じスロットへ書き戻す。
     * そのスロットが使用中の手（{@code usingHand}）なら、初回スタンプを見送る（中断回避）。
     */
    private static void addEquip(List<LowDurabilityItem> out, PlayerInventory inv, EquipmentSlot slot,
                                 String slotLabel, double thresholdPercent, NamespacedKey uidKey,
                                 boolean assignIdentity, EquipmentSlot usingHand) {
        addIfLow(out, inv.getItem(slot), slotLabel, thresholdPercent, uidKey, assignIdentity,
                it -> inv.setItem(slot, it), slot == usingHand);
    }

    /**
     * アイテムが破壊可能で閾値未満なら found に追加する。
     *
     * <p>重複抑止キーは固有 ID（PersistentDataContainer に保存した UUID）を使う。
     * 既に刻まれていればそれを採用し、無く、かつ assignIdentity が true なら新規 UUID を刻んで
     * {@code writeBack} でスロットへ書き戻す。これにより、同じ種別・同じ表示名のアイテムでも個体ごとに
     * 区別でき、スロット間を移動してもキーが変わらない（＝一度通知したら回復するまで再通知されない）。
     *
     * <p>ただし、使用中（{@code itemInUse}）のアイテムへ初回の固有 ID を書き戻すと弓を引く・食べる等の
     * 使用アクションが中断され得るため、その tick はスタンプを見送り、次回スキャンで検知・通知する。
     *
     * @param writeBack 固有 ID を刻んだ item を元のスロットへ書き戻す処理
     * @param itemInUse この item が現在使用中なら true。初回スタンプの書き戻しによる使用中断を避けるため見送る。
     */
    private static void addIfLow(List<LowDurabilityItem> out, ItemStack item, String slotLabel,
                                 double thresholdPercent, NamespacedKey uidKey, boolean assignIdentity,
                                 Consumer<ItemStack> writeBack, boolean itemInUse) {
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

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uid = pdc.get(uidKey, PersistentDataType.STRING);
        String key;
        if (uid != null) {
            key = "UID:" + uid;
        } else if (assignIdentity) {
            // 使用中アイテムへの書き戻しは使用アクションを中断し得るので、この tick は見送る
            // （固有 ID 未付与＝まだ通知していないので、次回スキャンで検知・通知すればよい）。
            if (itemInUse) {
                return;
            }
            uid = UUID.randomUUID().toString();
            pdc.set(uidKey, PersistentDataType.STRING, uid);
            item.setItemMeta(meta);
            writeBack.accept(item);
            key = "UID:" + uid;
        } else {
            // 固有 ID 未付与（書き込みを伴わない経路）。/check は重複抑止にキーを使わないので種別＋表示名で代用。
            key = "NOID:" + item.getType().name() + "#"
                    + PlainTextComponentSerializer.plainText().serialize(name);
        }
        out.add(new LowDurabilityItem(key, slotLabel, name, remaining, max, percent));
    }
}
