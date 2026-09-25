# FAQ

## Why are only regions with players listed?

Folia's **public API does not expose a list of regions**, or their identifiers — there is no method
that says "give me every region that currently exists." The only reliable way to find a region is to
have a player (or, for the global region, the server itself) standing in it and ask that thread whether
a given chunk belongs to it.

Some other Folia tools work around this by reading Folia's **internal classes** (reflection into things
like `ThreadedRegionizer` or `TickRegions$TickRegionData`), which lets them see every region, including
empty ones. TickRadar deliberately does **not** do that: it uses the public API only, which means it
keeps working across Folia updates without needing a new release every time Folia's internals change —
but it also means it can only see regions that currently have at least one player in them (plus the
always-visible global region).

If you need a **full picture** of every loaded region, including ones with no players — for example a
farm running in the background — use Folia's built-in `/tps` command or the
[spark](https://spark.lucko.me/) profiler, both of which read the internal region data directly.
TickRadar's `/tickradar regions` footer says exactly this: *"Only regions with players are listed. Use
/tps (Folia) or spark for a full view."*

A future version may add **watch points** (named locations you configure, like your spawn or a known
farm) so TickRadar can measure specific unpopulated regions too — that is planned, not available in
v1.0.

## Why does the TPS/MSPT shown update only every few seconds, not every tick?

TickRadar samples each region **once per `sampling.interval-ticks`** (20 ticks = 1 second by default),
not every tick — sampling every tick would add real cost to a Folia region's tick budget for no
practical benefit, since a single tick's numbers are too noisy to act on anyway. The region's TPS
(read through `getRegionTPS`) is itself polled by a dedicated background thread every
`debug.region-tps-interval-seconds` (5 seconds by default, hidden setting), independent of the sampling
interval. Between samples, TickRadar keeps showing the last value it measured; `—` means "not measured
yet," not "zero."

If you lower `sampling.interval-ticks`, updates get more frequent, at a (small) extra cost on every
populated region's tick.

## Why does an alert take longer than `trigger-samples` to fire?

Because the MSPT TickRadar reads for a region is already a **5-second moving average** computed by the
server, not an instant reading — that average needs a few seconds to reflect a spike (or a recovery)
before TickRadar's own `trigger-samples` (or `recovery-samples`) counter can even start climbing. In
practice, with the defaults (`trigger-samples: 5`, 1 sample/second), expect up to about 10 seconds between
a region getting slow and the alert firing (sooner for a sharp spike), not the bare 5 seconds `trigger-samples` alone would
suggest. See [alerts.md](alerts.md#alert-timing) for the full breakdown, including recovery.

## Two regions merged — why didn't I get a message for both?

When Folia merges two regions into one, TickRadar keeps a single alert state: the older region's
identifier survives, along with its worst alert level and sample counters, and the other region is
simply dropped. If the surviving region later recovers, the `RECOVERED` message is sent under the kept
identifier only — the merged-away region never gets its own `ENDED` or `RECOVERED` message.

## Why can't I see a plugin like TAB's header alongside TickRadar's tab footer?

TickRadar only ever writes the **tab footer**, never the header, specifically so it can coexist with a
tab plugin that manages the header. If another tab plugin also writes the footer (TAB, for example),
the two will fight over it — TickRadar warns about this at startup if it detects such a plugin. In that
case, disable `display.tab.enabled` in `config.yml` and use the `%tickradar_...%` placeholders from
that plugin's own configuration instead — see [placeholders.md](placeholders.md).

## Why doesn't TickRadar run on my Paper/Purpur/Spigot server?

Because it can't measure anything meaningful there. Folia's per-region TPS/MSPT model doesn't exist on
those servers — they have a single main thread, and the numbers Paper's own `/tps` or spark already
give you are the right tool. See [installation.md](installation.md) for exactly what happens when
TickRadar detects it's not on Folia.

## Is TickRadar a profiler or an optimizer?

No. It's a health dashboard: it tells you *which* region is slow and lets you jump there. For *why* a
region is slow, use [spark](https://spark.lucko.me/). For automatically reducing load (entity limits,
item merging...), use a dedicated optimizer plugin.
