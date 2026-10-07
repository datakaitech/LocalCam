# LocalCam · by DataKai

LocalCam turns an Android phone into a local camera source for Windows and OBS. Connect over Wi-Fi or prototype USB, with no watermark, account or hosted video backend. Licensed under [MIT](LICENSE).

**Development preview:** Android 0.8 and Windows 0.8.1. Windows 0.8.1 fixes portrait clipping when rotation and mirroring are combined. Physical-device compatibility and sustained FPS still need testing.

## Features

- Native Windows receiver and Android CameraX app.
- Same-network Wi-Fi pairing with a QR code and pinned TLS.
- Prototype USB connection through Android platform-tools and USB debugging.
- Full phone view, portrait/landscape orientation and phone fullscreen preview.
- Camera selection, focus, torch, resolution/FPS targets and adaptive JPEG compression.
- Windows fit/fill, display aspect, mirror, rotation and zoom.
- Local OBS Browser Source, with OBS Virtual Camera for meeting apps.

4K, microphone audio, dual-camera Studio mode, hardware video encoding, background blur and independent Windows virtual camera output are **planned and unavailable**. A 30 FPS target is not a guarantee on every phone or connection.

## Getting started

Build instructions are in [docs/BUILDING.md](docs/BUILDING.md). Install the Android APK and run the Windows app. Choose Wi-Fi on both devices and scan the desktop QR from the phone on the same local network. Internet access is not needed for streaming.

For USB, enable USB debugging, authorize the PC and use Connect Android over USB in Windows; approve the connection on the phone. This prototype requires Android platform-tools.

For the complete scene, select Full phone view on Android and Source / Fit / 1x on Windows. Tap Fullscreen to hide phone controls; Exit fullscreen or Android Back restores them.

Add an OBS Browser Source using http://127.0.0.1:8787/obs and refresh it after updates. Close the desktop to stop its receiver. Keep Android in the foreground while streaming.

## Repository

| Directory | Purpose |
| --- | --- |
| android/ | Android application and Gradle wrapper |
| desktop/ | WPF receiver, local server and OBS presentation |
| scripts/ | Build, package and regression checks |
| branding/ | LocalCam brand assets |
| docs/ | Build instructions and architecture |

Generated artifacts, toolchains, downloads, signing files and local settings are ignored. They can remain on a developer's machine without becoming public source. Packaged downloads belong in release assets, rather than the source repository.

The product website is maintained and deployed separately. In the development workspace its web root is ../localcam-website/website; the legacy website/ directory here is ignored. Application packaging does not require that sibling project.

## Contributing and security

Read [CONTRIBUTING.md](CONTRIBUTING.md), [the architecture](docs/ARCHITECTURE.md) and [SECURITY.md](SECURITY.md). [VALIDATION.txt](VALIDATION.txt) distinguishes automated results from outstanding physical-device tests. [SECURITY.txt](SECURITY.txt) documents trust boundaries and store-release work.

Frames are held in memory for local preview and OBS. LocalCam has no recording, telemetry or cloud upload service. Other applications on the paired computer can access or record its intended local output.

Current APKs use a development signing key; the release bundle is unsigned. Play Store publication still requires production signing, policy declarations and device testing.

A DataKai project · [datakai.in](https://datakai.in)
