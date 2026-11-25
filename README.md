# Online Flipper : Zero

Android application for consent-based pairing and management of devices you own or are authorized to manage.

## Current build

- Exact launcher name: **Online Flipper : Zero**.
- Dark monochrome Android interface.
- QR invitation creation and scanning.
- Locally saved device list.
- Device detail/control-panel layout.

## Current limitations

The Android client currently creates and parses QR invitations locally. It does not yet establish a live remote-control session. The Cloudflare Worker in `cloudflare/` is a pairing foundation and must be deployed and connected to the Android app. A second Android installation with explicit owner approval and permissions is also required.

The UI must not present unsupported actions as working. Android system permissions and user confirmation cannot be bypassed. No hidden screen capture, stealth access, or arbitrary remote shell is provided.

## Build

GitHub Actions builds the debug APK. Download the `Online-Flipper-Zero-debug` artifact from a successful Android workflow run.
