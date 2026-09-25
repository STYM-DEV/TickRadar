# PlaceholderAPI

TickRadar registers a [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
expansion, identifier **`tickradar`**, only if PlaceholderAPI is installed and enabled. It is never a
hard dependency.

All placeholders read from TickRadar's in-memory cache only — never from the game's API directly — so
they are safe to call from any thread (PlaceholderAPI calls expansions directly on the caller's own
thread).

## Placeholders

| Placeholder | Value |
|---|---|
| `%tickradar_region_tps%` | TPS (5 s window) of the player's region, one decimal (always a decimal point, whatever the language). |
| `%tickradar_region_tps_1m%` | TPS (1 min window) of the player's region. |
| `%tickradar_region_mspt%` | MSPT (5 s average) of the player's region, in ms. |
| `%tickradar_region_mspt_max%` | Maximum MSPT over the configured history window (`sampling.history-seconds`). |
| `%tickradar_region_players%` | Number of players observed in the same region. |
| `%tickradar_region_status%` | Translated status text: `ok`, `warning`, `critical` or `unavailable` (in the player's language). |
| `%tickradar_region_color%` | A legacy color code for the status: `&a` (ok), `&e` (warning), `&c` (critical), `&7` (unavailable). |
| `%tickradar_global_tps%` | TPS (5 s window) of the global region. |
| `%tickradar_global_mspt%` | MSPT (5 s average) of the global region. |
| `%tickradar_worst_mspt%` | MSPT of the slowest populated region currently observed. |
| `%tickradar_regions%` | Number of populated regions currently observed. |

## The `_raw` suffix

Every **numeric** placeholder above also exists with a `_raw` suffix — for example
`%tickradar_region_mspt_raw%` — which returns the unrounded, unformatted value (`Double.toString`, TPS
not capped at 20). Use it if you want to do your own formatting or math. `_raw` does **not** exist for
`region_status` or `region_color` (they are already text, not numbers).

## Behavior on missing data

- Offline or unknown player, or a region that has not been measured (or that is stale — older than 3
  sampling intervals) → **empty string**, never an error or a placeholder-looking fallback.
- The expansion is only loaded if PlaceholderAPI is active, and is unregistered when TickRadar stops.

## Using these in your own format

TickRadar's own displays (boss bar, action bar, tab footer) do **not** currently expand other plugins'
placeholders inside their MiniMessage templates in v1.0 — that is planned for a later version. If you
want a combined display (TickRadar's values alongside another plugin's), use a tab plugin such as
[TAB](https://www.spigotmc.org/resources/tab-reloaded.57806/) and TickRadar's placeholders directly,
rather than TickRadar's built-in tab footer.
