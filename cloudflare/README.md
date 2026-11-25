# Online Flipper : Zero — Cloudflare service

This Worker provides consent-based pairing and a role-aware WebSocket relay.

## Implemented

- One-time QR join code and 24-hour session expiry.
- Device join request and explicit owner approval.
- Separate owner and companion tokens.
- Authenticated WebSocket connections.
- Owner-to-companion command relay restricted to `get_status`, `vibrate`, `ring`, and `stop_ring`.
- Companion-to-owner results limited to known device-status fields.
- Session revocation and expiry.

This is not a general-purpose remote-control agent. No shell, hidden monitoring, screen capture, arbitrary app control, or bypass of Android permissions is provided.

## Deploy from GitHub Actions

1. In Cloudflare, create an API token with permission to edit Workers and manage the account's Durable Objects.
2. In GitHub, open **Settings → Secrets and variables → Actions**.
3. Add `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` as repository secrets.
4. Open Actions and manually run **Deploy Online Flipper : Zero pairing service**.
5. Open the successful run and copy the Worker URL, for example `https://online-flipper-pairing.<your-subdomain>.workers.dev`.
6. Put that exact URL in `RemoteConnection.BASE_URL` in the Android source, commit it, and install the resulting APK on both phones.

Deployment cannot be completed until the repository owner adds those secrets.

## Endpoints

- `GET /health`
- `POST /api/sessions` with `{"duration":"24h"}`
- `POST /api/sessions/:id/join`
- `POST /api/sessions/:id/join/status`
- `GET /api/sessions/:id/status` with `X-Owner-Token`
- `POST /api/sessions/:id/approve`
- `POST /api/sessions/:id/revoke`
- `GET /api/sessions/:id/ws?token=...` (WebSocket)

The Android companion must remain open for this initial live-command implementation. Keep the Worker URL and access tokens private.
