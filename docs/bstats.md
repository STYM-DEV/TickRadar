# bStats

TickRadar can report anonymous usage statistics to [bStats](https://bstats.org), the same service used
by most Paper/Folia plugins, so its adoption can be measured honestly.

## What is sent

Through bStats' standard Bukkit metrics (server count, player count, server software and version, Java
version — collected by bStats itself, not by TickRadar), plus a few custom, non-identifying charts:

- `language`: the language configured in `config.yml` (`auto`, `en`, `fr`, ...).
- `discord_alerts`: `yes` or `no` — whether alerts are enabled and Discord is one of the notified
  channels with a configured webhook. **Never the webhook URL itself, never whether it currently
  works.**
- `displays_enabled`: which of the boss bar, action bar and tab footer displays are enabled
  server-wide.

**Never sent**: player names, IP addresses, world names, coordinates, the Discord webhook URL, or
anything else identifying your server or players.

## How to opt out

Either is enough on its own:

- Set `metrics: false` in TickRadar's `config.yml`. This is **read at startup only** — restart the
  server after changing it (a plain `/tickradar reload` does not apply it).
- Or disable bStats globally for the whole server in `plugins/bStats/config.yml` (the switch that
  applies to every bStats-enabled plugin, not just TickRadar).

## Public dashboard

TickRadar's aggregated, anonymous statistics are public: <https://bstats.org/plugin/bukkit/TickRadar/34279>.

## Notes

- Sending starts a few minutes after the server boots and repeats roughly every 30 minutes, from
  bStats' own background thread — it never runs on a game thread.
