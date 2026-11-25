# NEXFLIP — Device Control

NEXFLIP is being rebuilt as a focused Android companion for pairing and managing Android devices you own or are authorized to manage.

## Current 0.3.0 build

- Black-and-white interface with animated screen transitions.
- One-tap QR invitation generation (24-hour expiry).
- QR invitation scanning.
- Locally saved device list.
- Device detail page with a structured control-panel layout.
- Saved devices remain on this phone after the app is closed.

## Important connection status

This build creates and reads local QR invitation payloads and saves scanned invitations. It does **not** yet establish a live device connection. The Cloudflare pairing worker must be deployed and connected to the Android client, and the second phone needs the NEXFLIP companion service with explicit Android permissions.

The control panel is intentionally marked as locked until the secure connection and companion permissions are implemented. No hidden access, silent screen capture, or permission bypass is used.

## Build

GitHub Actions builds the debug APK. Download the `Online-Flipper-Zero-debug` artifact from a successful Android workflow run.
