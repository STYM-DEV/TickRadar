# Commands and permissions

Root command: `/tickradar` (alias `/tradar`). Built with Brigadier; unknown or partial subcommands print
the help for the sender's own permissions.

## Commands

| Command | Description | Permission (default) | Notes |
|---|---|---|---|
| `/tickradar` | Shows the TPS and MSPT of your own region (5 s and 1 min TPS, 5 s MSPT, max MSPT over the history window), and how many players are observed there. | `tickradar.use` (**everyone**) | Console/RCON get the **global region**'s health instead of "your region". |
| `/tickradar bossbar\|actionbar\|tab [on\|off]` | Shows or hides one display for yourself. Without `on`/`off`, toggles it. | `tickradar.display` (**op**) | Players only. If the display is disabled server-wide (`display.<kind>.enabled: false`), you get a message saying so instead of toggling anything. |
| `/tickradar regions [page]` | Lists populated regions and the global region, slowest first, with world, approximate coordinates (X / Z of one of their players), player count, TPS, MSPT and a clickable teleport link. | `tickradar.admin.regions` (**op**) | Usable from console. Page size is `admin.regions-per-page`. |
| `/tickradar tp <id>` | Teleports you to the representative location of region `<id>` (e.g. `R12`, shown by `/tickradar regions` and in alerts). | `tickradar.admin.teleport` (**op**) | **Players only.** An unknown or expired region ID, or the global region `G` (no fixed location), gives a clear error instead of teleporting. |
| `/tickradar alerts [on\|off]` | Opts you in or out of receiving TickRadar alerts in game. | `tickradar.alerts` (**op**) | Players only. This is a per-player preference, independent of `alerts.notify` in `config.yml`. |
| `/tickradar discord test` | Sends a test message to the configured Discord webhook and reports the result (HTTP status, any rate-limit wait). | `tickradar.admin.discord` (**op**) | In RCON, the command replies immediately ("result will be written to the server console") and the actual outcome is logged there instead. |
| `/tickradar status` | Reports TickRadar's own health: measurement mode, whether `getRegionTPS` is available, sampling interval and history, per-stage timing costs, number of tracked regions and active alerts, and the Discord queue/delivery counters (webhook URL never shown). | `tickradar.admin.status` (**op**) | Usable from console. Output is plain text lines, one console line per bullet — meant for admins and for support requests, not for players. |
| `/tickradar reload` | Reloads `config.yml` and the language files (asynchronously) and applies the new settings. | `tickradar.admin.reload` (**op**) | In RCON, replies immediately and logs the actual result (success, success with warnings, or failure with the previous settings kept) in the console. |

## Permission tree (`plugin.yml`)

| Permission | Default | Grants |
|---|---|---|
| `tickradar.use` | `true` (everyone) | `/tickradar` (your own region's health) |
| `tickradar.display` | `op` | `/tickradar bossbar\|actionbar\|tab` |
| `tickradar.alerts` | `op` | `/tickradar alerts`, and receiving in-game alerts |
| `tickradar.admin.regions` | `op` | `/tickradar regions` |
| `tickradar.admin.teleport` | `op` | `/tickradar tp` |
| `tickradar.admin.status` | `op` | `/tickradar status` |
| `tickradar.admin.reload` | `op` | `/tickradar reload` |
| `tickradar.admin.discord` | `op` | `/tickradar discord test` |
| `tickradar.admin` | `op` | Parent of every permission above **except `tickradar.use`** |

To let every player see their region's health in a boss bar or the tab footer, grant
`tickradar.display` (and `tickradar.alerts` if you also want them to receive alerts) through your
permissions plugin — TickRadar itself keeps them op-only by default.

**One deliberate exception**: the clickable `[Teleport]` link shown in alerts (to holders of
`tickradar.alerts`) and in `/tickradar regions` (to holders of `tickradar.admin.regions`) is displayed
even to players who don't hold `tickradar.admin.teleport`. Clicking it runs `/tickradar tp <id>`, which
Brigadier then refuses for lack of that permission — so granting `tickradar.alerts` or
`tickradar.admin.regions` alone does not grant teleportation.
