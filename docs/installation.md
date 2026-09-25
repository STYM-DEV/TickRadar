# Installation

## Requirements

- **Folia 26.1.2 or newer** (26.2 works too; each new Folia version is re-tested before TickRadar
  supports it). TickRadar does **not** run on Paper, Purpur, Pufferfish or Spigot — see below.
- **Java 25** on the server (Folia 26.x already requires it).
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) 2.11.7+, if you
  want TickRadar's values in another plugin (TAB, a scoreboard...).

## Steps

1. Download `TickRadar-<version>.jar` and drop it in your server's `plugins/` folder.
2. Restart the server (TickRadar copies its default `config.yml` and `lang/en.yml` /
   `lang/fr.yml` on first start; it never writes to disk after that, other than these files and
   whatever the server writes to a player's own data on connection).
3. Check the console: TickRadar should log its startup and, if `Server#getRegionTPS` is present (it is,
   on 26.1.2+), that region TPS polling has started.
4. In game, run `/tickradar` (permission `tickradar.use`, granted to everyone by default) to see the
   health of your current region.
5. The in-game displays (boss bar, action bar, tab footer) and the admin commands are reserved to
   operators by default — see [commands.md](commands.md) for the exact permissions.

## What happens on Paper (or any non-Folia server)

TickRadar's `plugin.yml` declares `api-version: '26.1'`. On **Paper 1.21.x**, this means **Paper itself
refuses to load the jar**, before TickRadar's code ever runs.

On **Paper 26.x** (or Purpur, Pufferfish, or any server that is not Folia but does accept the jar),
TickRadar detects the absence of Folia at the very start of `onEnable`, before registering anything
(no listener, no command, no task), and disables itself cleanly:

```
[TickRadar] TickRadar only runs on Folia (https://papermc.io/software/folia).
[TickRadar] This server runs Paper, which has a single main thread: use /tps or spark instead.
[TickRadar] TickRadar is now disabled. Nothing was changed on your server.
```

Nothing else is logged, no exception, no leftover task. If you see this message, TickRadar is simply
not for your server — use Paper's own `/tps` or the [spark](https://spark.lucko.me/) profiler instead.

Forks of Folia (Luminol, Canvas...) are detected the same way Folia is (server brand or the presence of
Folia's internal region server class), but are not officially tested or promised support.

## Upgrading

Replace the jar and restart. TickRadar does not migrate any stored data (there is none — see
[configuration.md](configuration.md)): your `config.yml` and `lang/*.yml` files are kept as they are,
and any new key introduced by a future version falls back to its default with a warning naming the file
and line if something in your file is invalid.
