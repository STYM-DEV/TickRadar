# Discord alerts

TickRadar can post alerts to a Discord channel through a **webhook** — no bot, no invite, nothing to
authorize beyond pasting a URL in `config.yml`.

## Creating a webhook

1. In Discord, open the target channel's settings → **Integrations** → **Webhooks** → **New Webhook**.
2. Give it a name and (optionally) an avatar — this is cosmetic; TickRadar also sends its own
   `discord.username` with every message (default `TickRadar`).
3. Copy the **Webhook URL**. It looks like:
   `https://discord.com/api/webhooks/123456789012345678/AbCdEf...`
4. Paste it into `discord.webhook-url` in `config.yml`, then run `/tickradar reload` (or restart).
5. Run `/tickradar discord test` (permission `tickradar.admin.discord`) to confirm delivery.

## Keep this URL secret

**Anyone who has your webhook URL can post messages in your channel — no login required.** Treat it
like a password:

- Never paste `config.yml` as-is into a support ticket, a screenshot, or a public repository.
- If it leaks, delete the webhook in Discord and create a new one (there is no way to "rotate" a
  webhook's token otherwise).
- TickRadar itself never logs the full URL — not in the console, not in `/tickradar status`, not in any
  error message. It only ever shows a masked form, e.g. `https://discord.com/api/webhooks/1234.../****`.

## Accepted URLs

Only `https://` URLs whose host is `discord.com`, `discordapp.com`, `ptb.discord.com` or
`canary.discord.com`, with a path matching `/api/webhooks/<id>/<token>` (optionally `/api/vN/webhooks/...`),
are accepted. Anything else disables Discord alerts with a warning explaining why, and TickRadar never
attempts to contact a URL outside these hosts.

## What gets sent, and what doesn't

- **Levels**: `discord.levels` controls which alert levels are sent (default: `critical`, `recovered`).
  Add `warning` if you want earlier notice.
- **Coordinates are off by default** (`discord.include-coordinates: false`) — they reveal where players
  are standing. Only turn this on for a **staff-only** channel.
- **No player names** are ever included in a Discord message, regardless of settings.
- Example, without coordinates: `Region R12 in world "world" (5 players) is at 52.1 ms (critical).`; with
  coordinates: `Region R12 in world "world" around X 1204, Z -3380 (5 players) is at 52.1 ms (critical).`
  Messages are in English, or in French if `language: fr` is forced in `config.yml`.
- Messages are grouped: after the first event, TickRadar waits 2 seconds to batch up to 10 alerts into
  a single message, to avoid flooding the channel during a wide slowdown.
- `discord.username` sets the display name shown for the webhook's messages (default `TickRadar`).

## Rate limits

TickRadar follows [Discord's rate-limit rules](https://docs.discord.com/developers/topics/rate-limits):
it reads the `X-RateLimit-Remaining` / `X-RateLimit-Reset-After` response headers and waits accordingly,
with a conservative floor of **one message per second**. On an HTTP 429, it waits the `retry_after`
Discord returns before trying again. No limit is hard-coded beyond that floor — Discord explicitly
discourages hard-coded limits.

A bounded queue (50 pending alerts) protects the server if Discord is slow or unreachable: if it fills
up, the oldest pending alert is dropped (and counted in `/tickradar status` as `discord_dropped`), never
the server tick. Nothing about this ever runs on a game thread.

## If Discord disables the webhook

Discord may return **401, 403 or 404** if the webhook was deleted, revoked, or the URL is otherwise
invalid. When that happens, TickRadar:

- Stops trying to send to that webhook — **immediately**, for every alert still queued.
- Logs a single warning: the webhook was refused, and it stays disabled **until the next
  `/tickradar reload`**.
- Does not retry it — Discord explicitly asks integrations not to keep hitting a webhook that returned
  404 (repeated invalid requests can get your server's IP temporarily blocked by Discord).

**What to do**: check the webhook still exists in your channel's Integrations settings. If it was
deleted, create a new one, update `discord.webhook-url`, and run `/tickradar reload`. Then confirm with
`/tickradar discord test`.

Other failures (network error, timeout, HTTP 5xx) are retried twice (after 2 s, then 4 s) before the
message is dropped and counted as failed — this does **not** disable the webhook, it only affects that
one batch.

## Checking the current state

`/tickradar status` reports a `discord=<state>` line (`off`, `invalid-url`, `ready`, `rate-limited`,
`disabled`, or `stopped`) plus `discord_queue=`, `discord_dropped=`, `discord_sent=`, `discord_failed=`
and `discord_last_http=` counters. `discord_queue` and `discord_dropped` count alerts; `discord_sent` and
`discord_failed` count messages (one message can group several alerts). The webhook URL never appears
there.
