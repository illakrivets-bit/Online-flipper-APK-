# NEXFLIP

NEXFLIP is an Android companion app for connecting to and controlling compatible devices through supported interfaces.

## Planned modules

- Bluetooth device discovery and supported-device controls
- Wi-Fi / local-network integrations for devices with documented control interfaces
- Infrared control through a phone IR blaster or compatible external accessory
- NFC tag reading and permitted interactions
- Smart-home integrations
- Device discovery and connection status

## Current version

The first Android project scaffold and dark Flipper-inspired interface are in place. The module screens are currently interface previews; real device integrations will be added module by module.

## Build

The GitHub Actions workflow builds a debug APK on pushes to `main`. Open the repository's **Actions** tab, select the latest successful **Build NEXFLIP APK** run, and download the `NEXFLIP-debug` artifact.

## Safety

Use NEXFLIP only with devices and networks you own or are explicitly authorized to manage. The app will use supported pairing and control interfaces; it is not designed to bypass locks, access controls, or network security.
