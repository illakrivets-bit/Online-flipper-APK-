# Online flipper : Zero

A compact Android app for connecting to devices you own or are authorized to manage.

## Main screen
- Bluetooth LE: scan, pair, connect to compatible peripherals, inspect advertised GATT services and disconnect.
- Wi-Fi: discover local devices that advertise supported mDNS services and open available web interfaces.
- QR connect: scan a QR code containing an HTTP or HTTPS device page and open it.
- QR Studio: create QR codes from links or text, copy QR content, save QR images as PNG, and revisit recent QR content from local history.

The interface uses Android safe-area insets so content stays clear of the status and navigation bars. The main screen keeps the core connection tools accessible.

## Important limits
A QR scan opens a device's web page; it does not magically pair with every device. Direct control requires the device maker's supported protocol and any required authorization. Unknown Bluetooth commands are not sent.

QR history is stored locally on the device. PNG export uses the Android media library and is supported on Android 10 and newer.

## Build
GitHub Actions builds a debug APK on pushes to main. Download the `Online-Flipper-Zero-debug` artifact from a successful workflow run.
