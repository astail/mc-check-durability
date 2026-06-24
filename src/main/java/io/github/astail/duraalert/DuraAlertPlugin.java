package io.github.astail.duraalert;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DuraAlert 本体。
 * 一定間隔で各プレイヤーの装備・手持ち・インベントリを走査し、残り耐久値が閾値(%)を
 * 下回ったアイテムを本人のチャットへ通知する。サーバー側のみで動作（クライアント MOD 不要）。
 */
public final class DuraAlertPlugin extends JavaPlugin implements Listener {

    /** ベル音（通知時に鳴らす）。 */
    private static final Sound ALERT_SOUND =
            Sound.sound(Key.key("block.note_block.bell"), Sound.Source.MASTER, 0.8f, 1.0f);

    /** プレイヤーごとに「すでに通知済み」のアイテムキー集合。閾値を下回ったまま重複通知しないために使う。 */
    private final Map<UUID, Set<String>> warned = new HashMap<>();
    /** 低耐久アイテムへ個体識別用の固有 ID を保存する PersistentDataContainer のキー。 */
    private NamespacedKey uidKey;
    /** /duraalert mute で通知を一時停止しているプレイヤー（永続化しない＝再起動でリセット）。 */
    private final Set<UUID> muted = new HashSet<>();

    private boolean active;
    private double thresholdPercent;
    private int checkIntervalTicks;
    private boolean notifySound;
    private boolean checkInventory;

    private BukkitTask task;

    @Override
    public void onEnable() {
        uidKey = new NamespacedKey(this, "uid");
        saveDefaultConfig();
        loadSettings();
        register();
        startTask();
        getLogger().info("DuraAlert を有効化しました（閾値: " + thresholdPercentInt()
                + "% / 状態: " + (active ? "ON" : "OFF") + "）。");
    }

    @Override
    public void onDisable() {
        stopTask();
        warned.clear();
        muted.clear();
    }

    /** plugin.yml のコマンドとリスナーを登録する。失敗時はプラグインを無効化する。 */
    private void register() {
        PluginCommand command = getCommand("duraalert");
        if (command == null) {
            getLogger().severe("plugin.yml に duraalert コマンドが定義されていません。プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        DuraAlertCommand handler = new DuraAlertCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getServer().getPluginManager().registerEvents(this, this);
    }

    /** config.yml を読み直して設定値を反映する。 */
    private void loadSettings() {
        reloadConfig();
        FileConfiguration config = getConfig();
        active = config.getBoolean("enabled", true);
        thresholdPercent = clamp(config.getDouble("threshold-percent", 10.0), 1.0, 100.0);
        checkIntervalTicks = Math.max(20, config.getInt("check-interval-ticks", 100));
        notifySound = config.getBoolean("notify-sound", true);
        checkInventory = config.getBoolean("check-inventory", true);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ───────────────────────── スケジューラ ─────────────────────────

    private void startTask() {
        stopTask();
        task = getServer().getScheduler().runTaskTimer(this, this::scanAll, checkIntervalTicks, checkIntervalTicks);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** 全オンラインプレイヤーを走査する（無効時は何もしない）。 */
    private void scanAll() {
        if (!active) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers()) {
            notifyLowItems(player);
        }
    }

    // ───────────────────────── イベント ─────────────────────────

    /** 退出時に保持していた状態を破棄してメモリリークを防ぐ。 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        warned.remove(id);
        muted.remove(id);
    }

    // ───────────────────────── 通知ロジック ─────────────────────────

    /**
     * 1 プレイヤーを走査し、新たに閾値を下回ったアイテムだけを通知する。
     * すでに通知済みのアイテムは、いったん閾値より回復／消失するまで再通知しない。
     */
    private void notifyLowItems(Player player) {
        if (muted.contains(player.getUniqueId()) || !player.hasPermission("duraalert.notify")) {
            return;
        }
        // 定期スキャンでは固有 ID を未付与のアイテムへ刻む（assignIdentity=true）。
        List<LowDurabilityItem> low = DurabilityScanner.scan(player, thresholdPercent, checkInventory, uidKey, true);

        Set<String> already = warned.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        Set<String> currentKeys = new HashSet<>();
        int newly = 0;
        for (LowDurabilityItem item : low) {
            currentKeys.add(item.key());
            if (already.add(item.key())) {
                player.sendMessage(buildLine(item));
                newly++;
            }
        }
        // 閾値より回復した／無くなったアイテムは通知済み状態から外す（次に下回ったら再び通知できるように）。
        already.retainAll(currentKeys);

        if (newly > 0 && notifySound) {
            player.playSound(ALERT_SOUND, Sound.Emitter.self());
        }
    }

    /**
     * 現在の低耐久アイテムを本人へ一覧表示する（/duraalert check 用）。
     * 通知済み状態やミュートは無視し、その時点の状態をそのまま見せる。
     *
     * @return 見つかった低耐久アイテム数。
     */
    public int reportLowItems(Player player) {
        // /check は現状を表示するだけ。アイテムは書き換えない（assignIdentity=false）。
        List<LowDurabilityItem> low = DurabilityScanner.scan(player, thresholdPercent, checkInventory, uidKey, false);
        if (low.isEmpty()) {
            player.sendMessage(prefix().append(Component.text(
                    "耐久値が " + thresholdPercentInt() + "% を下回っているアイテムはありません。",
                    NamedTextColor.GREEN)));
            return 0;
        }
        player.sendMessage(prefix().append(Component.text(
                "耐久値が低いアイテム（" + low.size() + " 個）:", NamedTextColor.YELLOW)));
        for (LowDurabilityItem item : low) {
            player.sendMessage(buildLine(item));
        }
        return low.size();
    }

    /** 通知 1 行を組み立てる。例: [DuraAlert] ダイヤモンドの剣 (メインハンド) の耐久値が残り 8%（123/1561）です！ */
    private Component buildLine(LowDurabilityItem item) {
        return prefix()
                .append(item.name().colorIfAbsent(NamedTextColor.GOLD))
                .append(Component.text(" (" + item.slotLabel() + ") ", NamedTextColor.GRAY))
                .append(Component.text("の耐久値が残り ", NamedTextColor.YELLOW))
                .append(Component.text(item.displayPercent() + "%", NamedTextColor.RED))
                .append(Component.text(
                        "（" + item.remaining() + "/" + item.max() + "）です！", NamedTextColor.YELLOW));
    }

    private Component prefix() {
        return Component.text("[DuraAlert] ", NamedTextColor.AQUA);
    }

    // ───────────────────────── 公開ヘルパー（コマンドから利用） ─────────────────────────

    /** config と監視タスクを再読み込みし、通知済み状態をリセットする。 */
    public void reloadAll() {
        loadSettings();
        warned.clear();
        startTask();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean value) {
        this.active = value;
        getConfig().set("enabled", value);
        saveConfig();
    }

    public int thresholdPercentInt() {
        return (int) Math.floor(thresholdPercent);
    }

    public int checkIntervalSeconds() {
        return checkIntervalTicks / 20;
    }

    public boolean isNotifySound() {
        return notifySound;
    }

    public boolean isCheckInventory() {
        return checkInventory;
    }

    public boolean isMuted(Player player) {
        return muted.contains(player.getUniqueId());
    }

    public void setMuted(Player player, boolean mute) {
        if (mute) {
            muted.add(player.getUniqueId());
        } else {
            muted.remove(player.getUniqueId());
        }
    }
}
