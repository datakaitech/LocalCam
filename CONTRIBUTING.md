# Contributing to LocalCam

LocalCam is a DataKai project licensed under MIT. Contributions are welcome through issues and pull requests once the public repository is available.

Read [BUILDING.md](docs/BUILDING.md) and [ARCHITECTURE.md](docs/ARCHITECTURE.md). Describe the problem, the resulting behaviour and the checks you ran. For camera changes, include phone model, Android version, connection type, resolution, capture/sent/received FPS and conversion timings when available. Do not attach private video, pairing codes, certificate fingerprints, tokens or signing material.

Keep camera traffic local, preserve certificate pinning and token validation, and retain bounded queues. Camera or microphone capture must require explicit user action and the appropriate permission. Keep planned features visibly disabled until implemented. Changes to frame format or orientation must cover both Android and Windows/OBS; rendering tests alone do not establish physical-device FPS.

Run checks relevant to your change. The portrait regression test renders the production WPF layout, while transport tests start a temporary receiver and require the normal GUI to be closed. Explain physical-device checks that remain outstanding.

Never commit artifacts, downloads, local SDK paths, caches, signing keys or personal configuration. Generated files can remain on your development machine. If an ignored file was already tracked, ignoring it does not remove it from repository history: review it before publishing. Report security issues privately using [SECURITY.md](SECURITY.md).
