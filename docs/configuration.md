# Configuration

`plugins/TickRadar/config.yml` is created on first start. Every key below is documented in the file
itself; this page repeats it with the exact defaults, bounds and validation rules from the code.

## General rule for invalid values

TickRadar never crashes on a bad `config.yml`. For every key:

- **Unknown key** → a warning naming the file and line, then ignored.
- **Invalid value** (wrong type, out of bounds, or fails a cross-check like `warning < critical`) → the
  safest default is used, with a warning naming the file and line.
- **Unreadable file** (parse error) → at startup, defaults are used and an error is logged; on
  `/tickradar reload`, the **previous** settings are kept and the command reports the error.

## The shipped `config.yml`

```yaml
# TickRadar - region-aware TPS/MSPT for Folia.
# On Folia there is no single server-wide TPS: every region (an area of loaded chunks) ticks on
# its own, so TickRadar measures and alerts per region instead of for the whole server.
config-version: 1

# auto = client language of each player (fallback: en). Or force: en, fr.
language: auto

sampling:
  # How often each online player's region is checked, in ticks (20 ticks = 1 second). Minimum 10.
  interval-ticks: 20
  # How many seconds of past samples are kept to compute the average, max and 95th percentile
  # MSPT shown for a region.
  history-seconds: 60

# MSPT = milliseconds per tick: how long a region takes to compute one tick of the game.
# A region needs to finish each tick in 50 ms or less to keep 20 TPS (ticks per second); above
# 50 ms it starts running below 20 TPS, and gameplay on it slows down.
thresholds:
  # Early warning: MSPT above this value, but the region still keeps 20 TPS.
  warning: 40.0
  # The region has dropped below 20 TPS: players there will notice the slowdown.
  critical: 50.0

# Displays are for players with tickradar.display (op only by default).
# Give that permission to everyone if you want all players to see their region's health.
display:
  bossbar:
    enabled: true
    # Shown by default to players with tickradar.display (they can toggle it off).
    default-on: false
  actionbar:
    enabled: true
    default-on: false
    # The action bar stays fully opaque for 2 s then fades; TickRadar resends it just before
    # that fade, and right away when it is (re)shown or when the region's status changes.
  tab:
    enabled: true
    default-on: false
    # TickRadar only writes the tab FOOTER. If you use a tab plugin (e.g. TAB),
    # disable this and use the %tickradar_...% placeholders instead.

alerts:
  enabled: true
  # How many consecutive slow samples are needed before an alert fires. The MSPT TickRadar reads
  # is itself a 5-second average computed by the server, so an alert shows up at most about
  # trigger-samples sampling intervals + 5 seconds after a region starts slowing down (sooner
  # for a sharp spike). With the defaults (5 samples, one sample per second): 10 seconds at most.
  trigger-samples: 5
  # How many consecutive samples back under the warning threshold before the region is
  # considered recovered (same ~5 s delay applies, since it uses the same server average).
  recovery-samples: 10
  # Minimum delay, in seconds, between two alerts about the same region and level (avoids
  # spamming console/players/Discord while a region stays slow).
  cooldown-seconds: 300
  # Where alerts go: console, players (holders of tickradar.alerts), discord (see below).
  notify: [console, players, discord]
  global-region: true

discord:
  # Paste a Discord webhook URL to receive alerts in a channel. Empty = disabled.
  # KEEP THIS URL SECRET: anyone who has it can post in your channel.
  # Never share this file as-is (support tickets, screenshots, public repositories).
  webhook-url: ""
  # Alert levels sent to Discord: warning, critical, recovered.
  levels: [critical, recovered]
  # If true, Discord alerts also include the X/Z coordinates of the region (the world name is
  # always shown; never a player name). Off by default because coordinates reveal where players
  # are: only enable this for a staff-only channel.
  include-coordinates: false
  username: "TickRadar"

admin:
  regions-per-page: 8

# Anonymous usage statistics sent to bStats (https://bstats.org), a stats service used by most
# Paper/Folia plugins: number of servers and players, server and Java versions, TickRadar
# version, and a few settings (language, whether Discord alerts are configured, which displays
# are enabled). Never player names, IPs, world names or coordinates.
# Set to false to opt out, or turn off bStats for every plugin at once in
# plugins/bStats/config.yml. Either one is enough on its own.
# Read at startup only: restart the server (not just /tickradar reload) after changing it.
metrics: true

# Coming in 1.x (not active in 1.0):
# watch-points:
#   spawn: { world: world, x: 0, z: 0 }
```

## Key-by-key reference

| Key | Default | Bounds / accepted values | Notes |
|---|---|---|---|
| `config-version` | `1` | integer ≥ 1 | A value newer than what this TickRadar understands logs a warning; unknown keys are still ignored safely. |
| `language` | `auto` | `auto`, or a 2–3 letter language code (`en`, `fr`, ...) | `auto` reads each player's client language (fallback `en`); console and RCON always use `en` unless a language is forced. |
| `sampling.interval-ticks` | `20` | 10–200 | How often each online player's region is sampled, in ticks (20 = 1 s). |
| `sampling.history-seconds` | `60` | 10–300 | Rolling window used to compute the average, max and 95th percentile MSPT shown for a region. |
| `thresholds.warning` | `40.0` | must satisfy `0 < warning < critical` | MSPT (ms, see [alerts.md](alerts.md)) above which a region is "warning" — still at 20 TPS, but worth watching. Invalid combinations fall back to `40.0` / `50.0` with a warning. |
| `thresholds.critical` | `50.0` | must satisfy `0 < warning < critical` | MSPT (ms) above which a region is "critical" — it has dropped below 20 TPS. |
| `display.bossbar.enabled` | `true` | boolean | Turns the boss bar display off server-wide (players can't turn it back on with `/tickradar bossbar on`). |
| `display.bossbar.default-on` | `false` | boolean | Whether the boss bar is shown by default to players who hold `tickradar.display`. |
| `display.actionbar.enabled` / `.default-on` | `true` / `false` | boolean | Same as above, for the action bar. |
| `display.tab.enabled` / `.default-on` | `true` / `false` | boolean | Same as above, for the tab **footer** only (TickRadar never touches the tab header). |
| `alerts.enabled` | `true` | boolean | Turns the whole alert system off (console, players, Discord). |
| `alerts.trigger-samples` | `5` | 1–100 | Consecutive samples above a threshold before an alert fires. |
| `alerts.recovery-samples` | `10` | 1–1000 | Consecutive samples back under the warning threshold before a `recovered` alert fires. |
| `alerts.cooldown-seconds` | `300` | 0–86400 | Minimum delay between two alerts about the same region. |
| `alerts.notify` | `[console, players, discord]` | any of `console`, `players`, `discord` (case-insensitive) | Unknown values are ignored with a warning. Removing `players` still lets players opt in/out locally with `/tickradar alerts`, it just means the channel is off entirely. |
| `alerts.global-region` | `true` | boolean | Whether the global region (console commands, weather, time of day) is also monitored and can alert. |
| `discord.webhook-url` | `""` (disabled) | a `https://discord.com/api/webhooks/<id>/<token>` URL (also `discordapp.com`, `ptb.discord.com`, `canary.discord.com`) | See [discord.md](discord.md). Invalid URLs disable Discord alerts with a warning; the URL is never logged in full. |
| `discord.levels` | `[critical, recovered]` | any of `warning`, `critical`, `recovered` | Which alert levels are sent to Discord. |
| `discord.include-coordinates` | `false` | boolean | Coordinates reveal where players are; only enable for a staff-only channel. |
| `discord.username` | `TickRadar` | any non-blank text | Display name used by the webhook message. Blank falls back to `TickRadar`. |
| `admin.regions-per-page` | `8` | 1–50 | Page size of `/tickradar regions`. |
| `metrics` | `true` | boolean | Enables bStats. **Read at startup only**: changing it requires a restart, not just `/tickradar reload`. See [bstats.md](bstats.md). |

## Hidden `debug` keys

These keys are **not** present in the shipped `config.yml` — they exist for testing and advanced
tuning, and you can add them under a top-level `debug:` section if you need them:

| Key | Default | Bounds | Notes |
|---|---|---|---|
| `debug.region-tps-interval-seconds` | `5` | 1–60 | How often the dedicated `TickRadar-RegionTPS` thread polls `getRegionTPS` for each observed region. A non-integer or out-of-bounds value falls back to `5` with a warning naming the file and line. Applied on `/tickradar reload` (the thread restarts). |
| `debug.synthetic-anchors` | none | list of `{ world, x, z }` | Creates synthetic (player-less) measurement points, used for testing. Not intended for normal use. |

## Languages

- `lang/en.yml` and `lang/fr.yml` are copied into `plugins/TickRadar/lang/` on first start and are
  **never overwritten** by TickRadar.
- Edit them freely — messages are in [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/)
  format. Apply changes with `/tickradar reload`.
- Keys you don't override are filled in from TickRadar's built-in defaults, in memory (your file on
  disk is never rewritten).
- `language: auto` in `config.yml` picks each player's client language, falling back to English.

## Applying changes

`/tickradar reload` (permission `tickradar.admin.reload`) re-reads `config.yml` and the language files
asynchronously, then applies them: the sampling interval, thresholds, displays, alerts and the Discord
webhook all take effect immediately. `metrics` (bStats) is the only key that requires a server restart.
The server's own `/reload` command is **not** a supported way to reload TickRadar.
