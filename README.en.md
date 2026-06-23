# DuraAlert

A Paper plugin that **warns a player in their own chat when the durability of an item or piece of equipment they carry drops below a threshold** (server-side only).

Stop "I didn't notice my best tool was about to break." DuraAlert periodically checks your armor, hands, and inventory, and alerts you the moment an item's remaining durability falls under the threshold (**10% by default**).

> **No client mod required.** Just install the plugin on the server — vanilla clients get the notifications.

## The problem it solves

"My diamond pickaxe / elytra / netherite gear quietly wears out and breaks while I'm using it."
DuraAlert watches durability and **warns you in chat (and with a bell sound) when it gets low (below 10% by default).**

## Features

- **Automatic durability monitoring**: checks armor, both hands, and inventory items on an interval (every 5s by default).
- **Threshold alerts**: when remaining durability drops below the configured percentage (default 10%), the item is reported in the player's own chat. A bell sound can play too.
- **No spam**: once an item has been reported, it won't be reported again until it recovers (e.g. is repaired).
- **Choose the scope**: monitor equipment + both hands only, or include the whole inventory.
- **Manual check**: `/duraalert check` lists everything that's currently low.
- **Mute / global ON-OFF**: `mute` silences alerts for yourself; `on|off` toggles the whole server.

## Requirements

- Server: Paper 26.1.2 (build 69+)
- Java: 25
- Clients: vanilla (no mods, server-side only)

## Installation

1. Drop `DuraAlert-1.0.1.jar` into `plugins/` and restart.
2. That's it — monitoring starts automatically. When an item drops below 10%, you'll be notified in chat.

Example:

```text
[DuraAlert] Diamond Sword (main hand) durability is at 8% (124/1561)!
```

## Usage

Works out of the box with defaults. Tune behavior via `config.yml` (below) or in-game commands.

- See what's currently low → `/duraalert check`
- Silence alerts for yourself → `/duraalert mute` / restore → `/duraalert unmute`
- Show current settings → `/duraalert status`

## Commands

| Command | Description | Permission |
|---|---|---|
| `/duraalert check` | List items currently below the threshold | `duraalert.use` |
| `/duraalert status` | Show current settings and your mute state | `duraalert.use` |
| `/duraalert mute \| unmute` | Stop / resume alerts for yourself (resets on restart) | `duraalert.use` |
| `/duraalert on \| off` | Enable / disable monitoring globally | `duraalert.manage` |
| `/duraalert reload` | Reload the configuration | `duraalert.manage` |

Alias: `/da`

## Permissions

| Permission node | Description | Default |
|---|---|---|
| `duraalert.notify` | Receive low-durability notifications | `true` (everyone) |
| `duraalert.use` | Use `check` / `status` / `mute` | `true` (everyone) |
| `duraalert.manage` | `on` / `off` / `reload` (server-wide operations) | `op` |

## Configuration (`config.yml`)

```yaml
enabled: true            # whether monitoring/alerts run (toggle with /duraalert on|off)
threshold-percent: 10    # alert when remaining durability drops below this percent (1-100)
check-interval-ticks: 100 # how often to check, in ticks (20 ticks = 1s, min 20)
notify-sound: true       # play a bell sound on alert
check-inventory: true    # also check inventory items (false = equipment and both hands only)
```

## How it works / technical notes

- A repeating scheduler (`runTaskTimer`) scans each player's items: 4 armor slots, main hand, off hand, plus the inventory (excluding the held slot to avoid double counting) when `check-inventory: true`.
- Max durability prefers the data component (`getMaxDamage`) and falls back to the vanilla max; an item is flagged when `remaining / max * 100` is below the threshold. Unbreakable items and items without durability are ignored.
- Each low item is reported once when it crosses the threshold. Per-player state remembers what was reported and won't notify again until the item recovers (or leaves the inventory).
- Display names use `ItemStack#effectiveName()`, so anvil-renamed items show their custom name.

### Limitations

- Monitoring is poll-based. Lowering `check-interval-ticks` improves responsiveness but increases server load (minimum 1s).
- Mute state is not persisted (resets on server restart).

## Build

```bash
./deploy.sh        # macOS native (JDK 25 + Maven). Output: target/DuraAlert-1.0.1.jar
# or
mvn -B clean package
```

Pushing a `v*` tag triggers GitHub Actions (`.github/workflows/build.yml`) to build and attach the jar to a release.

## Deploying to a server

Place the jar in the server's `plugins/` and restart. See the Japanese [README.md](README.md) for Docker / `itzg/minecraft-server` auto-download instructions.

## License

MIT License — see [LICENSE](LICENSE).
