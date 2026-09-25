# Alerts

TickRadar watches the MSPT of every populated region (and, if enabled, the global region) and raises an
alert when it crosses a threshold for long enough — with hysteresis, so a region flapping around a
threshold doesn't spam your console or your staff.

## States and transitions

Each region (and the global region `G`) has its own alert state: `OK`, `WARNING` or `CRITICAL`.

| Transition | Condition | Event sent |
|---|---|---|
| OK → WARNING | MSPT ≥ `thresholds.warning` for `alerts.trigger-samples` consecutive samples | `WARNING` (unless still in cooldown) |
| OK/WARNING → CRITICAL | MSPT ≥ `thresholds.critical` for `alerts.trigger-samples` consecutive samples | `CRITICAL` (always sent, even during a `WARNING` cooldown) |
| CRITICAL → WARNING | never — TickRadar does not step back down a level; it waits for a full recovery | — |
| WARNING/CRITICAL → OK | MSPT < `thresholds.warning` for `alerts.recovery-samples` consecutive samples | `RECOVERED` (only if an alert was actually sent before) |
| A region in alert loses all its players | — | `ENDED` ("no longer observed") |

Samples where the region's health could not be measured are ignored: they neither count toward
`trigger-samples`/`recovery-samples` nor reset the count.

When two regions merge, TickRadar keeps a single alert state under the **older** region's identifier,
carrying over its worst alert level and its sample counters; the other region's identifier is simply
dropped. If that region later recovers, the `RECOVERED` message is sent under the **kept** identifier —
the merged-away region never gets its own `ENDED` or `RECOVERED` message.

## Alert timing

A region's MSPT is itself a moving average computed by the server over the last **5 seconds**, not an
instant reading, so a threshold crossing takes longer to show up than `trigger-samples` alone would
suggest: at most `trigger-samples` sampling intervals **plus** up to 5 seconds for the server's own
average to reflect the slowdown. With the defaults (`trigger-samples: 5`, `interval-ticks: 20` =
1 sample/second), that is about 10 seconds at most, and sooner for a sharp spike (about 9 s measured on
a real server). The
same delay applies in reverse to recovery: `recovery-samples` consecutive samples under `warning`, plus
up to 5 seconds for the server's MSPT average to catch up with the drop.

## Settings (`config.yml`)

- `thresholds.warning` / `thresholds.critical`: MSPT thresholds in milliseconds. Must satisfy
  `0 < warning < critical`, otherwise TickRadar falls back to `40.0` / `50.0` with a warning.
- `alerts.trigger-samples` (default `5`): consecutive samples above a threshold before it fires.
- `alerts.recovery-samples` (default `10`): consecutive samples back under `warning` before a region is
  considered recovered.
- `alerts.cooldown-seconds` (default `300`): minimum delay between two alerts about the same region and
  level. Discord and in-game alerts are subject to the same cooldown; you won't get repeated pings for
  a region stuck at the same level. An alert held back by the cooldown is sent as soon as, after
  the cooldown, the region's last `alerts.trigger-samples` consecutive samples are at or above that
  level's threshold. If a `CRITICAL` is held back while the `WARNING` level is not in cooldown, a
  `WARNING` is sent right away if none was sent yet for this slowdown, and the `CRITICAL` follows when its own cooldown ends
  if the region is still at or above `thresholds.critical`. A held-back alert that recovers first stays
  silent.
- `alerts.notify` (default `[console, players, discord]`): which channels receive alerts.
- `alerts.global-region` (default `true`): whether the global region (server-wide commands, weather,
  time of day) is monitored too.
- `alerts.enabled` (default `true`): turns the whole system off. When turned back on, the next
  measured sample for a region resets its alert machine from scratch — silently, without emitting a
  stale `RECOVERED` or `ENDED`, and forgetting any pending cooldown.

## Where alerts go

- **Console**: always, if `console` is in `alerts.notify`. `WARNING` and `CRITICAL` are logged at
  `WARN`, `RECOVERED` and `ENDED` at `INFO`.
- **Players**: holders of `tickradar.alerts` who have not opted out with `/tickradar alerts off`, if
  `players` is in `alerts.notify`. The message includes a clickable `[Teleport]` link.
- **Discord**: see [discord.md](discord.md), if `discord` is in `alerts.notify` and the level is in
  `discord.levels`.

## Message examples (English)

- In-game alert: `[TickRadar] Region R12 in world "world" around X 1204, Z -3380 (5 players) is at 52.1 ms (critical). [Teleport]`
- Recovery: `[TickRadar] Region R12 in world "world" around X 1204, Z -3380 has recovered: 31.0 ms.`
- A region no longer observed (everyone left): `Region R12 in world "world" around X 1204, Z -3380 is no longer
  observed: no players are left there.`
- The global region: `[TickRadar] The global region G is at 55.5 ms (critical).`
- The regions list footer: `Only regions with players are listed. Use /tps (Folia) or spark for a full view.`

`world` is the name of the world, and X / Z are the block coordinates of one of the players in the region
(the place `[Teleport]` takes you to), not the exact center of the region. The console gets the same text,
without the prefix and the link.

The same alert in French: `[TickRadar] La région R12 du monde « world », vers X 1204, Z -3380 (5 joueurs),
est à 52,1 ms (critique). [Téléporter]`. Messages in French use a decimal comma; the PlaceholderAPI values
and `/tickradar status` always use a decimal point, whatever the language.

All these texts are in `lang/en.yml` and `lang/fr.yml`, and can be edited freely (MiniMessage format).
