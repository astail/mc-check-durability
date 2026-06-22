package io.github.astail.duraalert;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** /duraalert コマンドの実処理とタブ補完。 */
public final class DuraAlertCommand implements CommandExecutor, TabCompleter {

    private final DuraAlertPlugin plugin;

    public DuraAlertCommand(DuraAlertPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check", "scan" -> requirePlayer(sender, plugin::reportLowItems);
            case "mute" -> requirePlayer(sender, player -> setMute(player, true));
            case "unmute" -> requirePlayer(sender, player -> setMute(player, false));
            case "on" -> { if (requireManage(sender)) setActive(sender, true); }
            case "off" -> { if (requireManage(sender)) setActive(sender, false); }
            case "reload" -> { if (requireManage(sender)) doReload(sender); }
            case "status" -> sendStatus(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ───────────── サブコマンド ─────────────

    private void setMute(Player player, boolean mute) {
        plugin.setMuted(player, mute);
        player.sendMessage(mute
                ? info("耐久値通知をミュートしました（/duraalert unmute で解除）。")
                : ok("耐久値通知のミュートを解除しました。"));
    }

    private void setActive(CommandSender sender, boolean value) {
        plugin.setActive(value);
        sender.sendMessage(ok("耐久値の監視・通知を " + (value ? "ON" : "OFF") + " にしました。"));
    }

    private void doReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(ok("設定を再読み込みしました（閾値: " + plugin.thresholdPercentInt()
                + "% / 間隔: " + plugin.checkIntervalSeconds() + " 秒）。"));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(info("DuraAlert: " + (plugin.isActive() ? "ON" : "OFF")
                + " / 閾値 " + plugin.thresholdPercentInt() + "%"
                + " / 間隔 " + plugin.checkIntervalSeconds() + " 秒"
                + " / インベントリ走査 " + (plugin.isCheckInventory() ? "あり" : "なし")
                + " / 通知音 " + (plugin.isNotifySound() ? "あり" : "なし")));
        if (sender instanceof Player player) {
            sender.sendMessage(info("あなたの通知: " + (plugin.isMuted(player) ? "ミュート中" : "ON")));
        }
        sendUsage(sender);
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(info("/duraalert check            - 今の低耐久アイテムを一覧表示"));
        sender.sendMessage(info("/duraalert mute | unmute    - 自分への通知を停止 / 再開"));
        sender.sendMessage(info("/duraalert status           - 現在の設定を表示"));
        if (sender.hasPermission("duraalert.manage")) {
            sender.sendMessage(info("/duraalert on | off         - 監視・通知の全体 有効 / 無効"));
            sender.sendMessage(info("/duraalert reload           - 設定を再読み込み"));
        }
    }

    // ───────────── 補助 ─────────────

    private boolean requireManage(CommandSender sender) {
        if (sender.hasPermission("duraalert.manage")) {
            return true;
        }
        sender.sendMessage(error("この操作（on / off / reload）はサーバー管理者のみ実行できます。"));
        return false;
    }

    private void requirePlayer(CommandSender sender, Consumer<Player> action) {
        if (sender instanceof Player player) {
            action.accept(player);
        } else {
            sender.sendMessage(error("このサブコマンドはプレイヤーが実行してください。"));
        }
    }

    private static Component ok(String text) {
        return Component.text(text, NamedTextColor.GREEN);
    }

    private static Component error(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    private static Component info(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    // ───────────── タブ補完 ─────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("check", "status", "mute", "unmute"));
            if (sender.hasPermission("duraalert.manage")) {
                subs.add("on");
                subs.add("off");
                subs.add("reload");
            }
            return prefix(subs, args[0]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> options, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
