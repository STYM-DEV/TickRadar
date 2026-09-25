# TickRadar

[![Build](https://github.com/STYM-DEV/TickRadar/actions/workflows/build.yml/badge.svg)](https://github.com/STYM-DEV/TickRadar/actions/workflows/build.yml)

**Folia only.** TickRadar shows the TPS and MSPT of the region a player is standing in, gives staff a
sorted view of populated regions, and alerts your team — in game, in the console, and on Discord —
when a region starts to lag.

> See which Folia region is lagging, before your players do.

On Folia, there is no single server-wide TPS anymore: every region ticks on its own thread. The usual
tools (a global `/tps`, most tab plugins) either don't run on Folia at all, or quietly average numbers
that no longer mean anything. TickRadar shows the number that actually matters: the health of **your**
region.

## Screenshots

*(to be added before release: boss bar and action bar in game, `/tickradar regions`, an in-game alert,
a Discord alert message)*

## Features

- Boss bar, action bar and tab footer showing TPS and MSPT of your own region.
- `/tickradar regions`: populated regions and the global region, slowest first, with a one-click
  teleport.
- Alerts (console, in game, Discord) when a region crosses a configurable MSPT threshold, with
  hysteresis so it doesn't spam you.
- PlaceholderAPI support.
- Public API only — no reflection into Folia's internals, so TickRadar keeps working across Folia
  updates instead of needing a new release for every internal change.

## Compatibility

- **Folia 26.1.2 and newer only.** TickRadar detects and cleanly disables itself on any other server
  software (Paper, Purpur, Spigot...) — see [the documentation](docs/installation.md) for exactly
  what that looks like.
- Requires Java 25 (Folia 26.x already does).

## Installation

1. Download the latest `TickRadar-<version>.jar` from the [releases](../../releases) page (or your
   marketplace of choice).
2. Drop it in `plugins/` on a Folia 26.1.2+ server and restart.
3. Run `/tickradar` in game to check your own region's health.

Full setup, every `config.yml` key, commands, permissions and Discord alerts: see
[docs/](docs/README.md).

## Documentation

- [docs/README.md](docs/README.md) — installation, configuration, commands, placeholders,
  alerts, Discord, bStats, FAQ.

## License

TickRadar is released under the [MIT License](LICENSE) by [STYM](https://stym.dev).

## About this project

TickRadar is developed by STYM as a free, open-source tool for the Folia community. Its code is written
with the help of AI coding agents.

## Support

Issues and questions: use this repository's issue tracker. There is no dedicated support Discord.
