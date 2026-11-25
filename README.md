# Online flipper : Zero

Android app for connecting to compatible devices you own or are authorized to manage.

## Implemented integrations

- Bluetooth Low Energy: scan, pair, connect, list discovered GATT services and disconnect.
- NFC: read compatible NDEF tags.
- Infrared: detect the phone's IR emitter and supported carrier ranges.
- Wi-Fi: discover local mDNS services such as HTTP devices, printers, Cast, AirPlay and HomeKit advertisements; open HTTP device pages.

## Limits

A connection does not automatically grant control. Each device needs its own supported protocol and pairing. Generic power-off commands do not exist across all brands. Unknown Bluetooth writes are not sent.

## Build

GitHub Actions builds a debug APK on pushes to `main`. Download the `Online-Flipper-Zero-debug` artifact from the latest successful workflow run.

Use only with devices and networks you own or have permission to manage.
