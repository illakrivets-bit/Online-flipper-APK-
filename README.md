# Online Flipper : Zero

Android-to-Android pairing and a small, consent-based remote action panel.

## Implemented in the current source

- Exact app name: **Online Flipper : Zero**.
- Black-and-white Compose interface.
- Server-created, one-time QR invitations (24-hour session).
- QR scanning on the companion phone.
- Explicit approval on the phone that created the invitation.
- HTTPS API and authenticated WebSocket transport.
- Saved controller devices; owner tokens are encrypted with Android Keystore.
- Allow-listed remote actions: request battery/device/storage status, vibrate, start a short sound signal, stop the signal.
- Revoke/unpair support.
- Companion only accepts commands while its app is open and connected.

No shell, hidden access, screen capture, remote app launching, or arbitrary command execution is included. The Android operating system retains control over protected functions.

## Required setup before pairing can work

1. Add GitHub repository Actions secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`.
2. Run the **Deploy Online Flipper : Zero pairing service** workflow.
3. Copy the deployed Worker URL (the `https://...workers.dev` address).
4. Replace `RemoteConnection.BASE_URL` in `app/src/main/java/com/illakrivetsbit/nexflip/RemoteConnection.kt` with that exact URL.
5. Commit the URL change and wait for a successful Android build.
6. Install the same new APK on both Android phones. Both phones need internet access.
7. On the controller phone, tap **Создать QR-код**. On the second phone, open the app and scan it. Approve the device on the controller phone.
8. Keep the companion app open for live commands. Closing it disconnects the live channel.

## Build

GitHub Actions creates the debug APK as the `Online-Flipper-Zero-debug` artifact after a successful Android workflow run.
