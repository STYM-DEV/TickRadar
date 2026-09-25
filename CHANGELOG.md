# Changelog

All notable changes to TickRadar are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and TickRadar follows
[Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-09-25

### Added

- Region-aware TPS and MSPT: each online player's region is sampled independently, grouped by an
  ownership test (`Bukkit.isOwnedByCurrentRegion`), with no reflection into Folia's internals.
- Per-player displays: boss bar, action bar and tab footer (`/tickradar bossbar|actionbar|tab [on|off]`),
  each toggleable and off by default for non-staff (`tickradar.display`, op by default). The action bar
  refreshes right away whenever a region's status changes (ok, warning, critical or unavailable), instead
  of waiting for its normal refresh pace.
- Readable coordinates everywhere a region is mentioned — in commands, alerts and Discord messages — for
  example `in world "world" around X 1204, Z -3380`.
- Messages in French use a decimal comma (`52,1 ms`) instead of a decimal point; English messages,
  PlaceholderAPI values and `/tickradar status` always use a decimal point, whatever the language.
- `/tickradar`: the health of your own region (or the global region, from console/RCON).
- `/tickradar regions [page]`: populated regions and the global region, slowest first, with a
  clickable `[Teleport]` link.
- `/tickradar tp <id>`: teleport to a region listed by `/tickradar regions` (players only).
- `/tickradar status`: TickRadar's own health (measurement mode, timing costs, tracked regions, active
  alerts, Discord queue and delivery counters).
- `/tickradar reload`: reload `config.yml` and the language files without a restart.
- Alerts with configurable MSPT thresholds, trigger/recovery sample counts and a per-region cooldown,
  sent to the console, to in-game players (`tickradar.alerts`) and to Discord. An alert held back by the
  cooldown is sent as soon as the cooldown ends, if the region is still at or above that level's
  threshold — instead of staying silent until the region fully recovers.
- Discord webhook integration (`/tickradar discord test`): rate-limit aware, retried on transient
  failures, automatically disabled on a rejected webhook (401/403/404) until the next reload, webhook
  URL never logged in full.
- PlaceholderAPI expansion (`%tickradar_...%`), with a `_raw` variant of every numeric placeholder.
- Optional, anonymous bStats reporting (`metrics: true` by default), documented and easy to opt out of.
- English and French translations (`lang/en.yml`, `lang/fr.yml`), user-editable and never overwritten.
- Clean, informative self-disabling on any non-Folia server (Paper, Purpur, Spigot...), before any
  listener, command or task is registered.

### Performance

- Player sampling is spread evenly across ticks, so a wave of players connecting, teleporting or
  respawning together does not pile up measurements on the same tick of a region.
- Lower average per-player sampling cost, measured with real players on Folia.
- Sampling and display code is warmed up off the game thread at startup and after every `/tickradar
  reload`, instead of paying a one-time delay on a player's first sample.

### Notes for this release

- **Folia only**, 26.1.2 and newer; requires Java 25 on the server.
- No stored data beyond the copied `config.yml`/`lang/*.yml` files and each player's own
  `PersistentDataContainer`: nothing to migrate, nothing to back up.
- TickRadar cannot see regions with no player in them (a Folia public-API limitation, not a bug) — use
  `/tps` (Folia) or spark for a full view. See the FAQ for details.
- Permissions granted or revoked while a player is online (`tickradar.display`, `tickradar.alerts`,
  `tickradar.admin.teleport`) take effect within three samples of that player: three sampling intervals
  (3 s by default), slightly more right after a teleport or a change of `sampling.interval-ticks`, or at
  once when the player toggles a display or their alerts.
- MIT license.
