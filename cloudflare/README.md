# Online Flipper: Zero — Cloudflare pairing service

This folder contains the Cloudflare Worker used for consent-based device pairing.

## What it currently implements

- Create a pairing session with a fixed 24-hour lifetime.
- Return a QR payload containing a one-time invitation code (never the owner token).
- A second device can request to join.
- The owner must approve the exact pending request.
- The approved device receives a separate access token.
- Revoke a session at any time.
- A 24-hour session expires automatically at its deadline, and the owner can revoke it earlier.
- WebSocket relay accepts only small, allow-listed event types.

This is a pairing/transport foundation, **not a general-purpose remote-control agent**. It does not provide shell access, hidden monitoring, screen capture, accessibility control, or arbitrary commands. Add narrowly scoped features only when the device owner explicitly enables and confirms them.

## Deploy

1. Install Node.js and Wrangler.
2. From this directory run `npm create cloudflare@latest` only if you want a fresh local setup; do not overwrite this Worker.
3. Install Wrangler in your own workspace: `npm install --save-dev wrangler`.
4. Run `npx wrangler login`.
5. Deploy with `npx wrangler deploy`.

The first deployment creates the Durable Object SQLite class from the migration in `wrangler.toml`.

## API

- `GET /health`
- `POST /api/sessions` body: `{"duration":"24h"}`
- `POST /api/sessions/:id/join` body: `{"joinCode":"...","deviceName":"My phone"}`
- `POST /api/sessions/:id/join/status` body: `{"requestToken":"..."}`
- `POST /api/sessions/:id/approve` body: `{"ownerToken":"...","requestId":"..."}`
- `GET /api/sessions/:id/status` header: `X-Owner-Token: ...`
- `POST /api/sessions/:id/revoke` body: `{"ownerToken":"..."}`
- `GET /api/sessions/:id/ws?token=...` WebSocket (approved device token)

The QR payload is JSON for the Android app to encode as a QR image. It is not a URL and must be parsed by the app.

## Important next steps

Before public deployment, add rate limiting at the edge, automated tests, log redaction, and an explicit owner-side approval UI in the Android app. Do not put owner tokens in QR codes, URLs, analytics, or logs.
