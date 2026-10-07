# Architecture

Android CameraX binds preview and analysis to a shared FIT viewport. Full phone view packs the YUV crop into NV21 and JPEG-encodes it without rotating pixels; EXIF metadata carries rotation and mirroring. Explicit crop modes transform pixels before encoding. A single-slot newest-frame queue prevents unbounded latency.

Wi-Fi uses a local TLS receiver with a per-session certificate fingerprint from the desktop QR. Uploads require a paired bearer token. The continuous transport is a sequence of four-byte big-endian lengths followed by JPEG bytes. USB currently uses Android platform-tools and adb reverse for the same transport; it requires USB debugging and phone confirmation.

Windows retains the newest JPEG in memory. The WPF viewer reads EXIF orientation, sizes the image explicitly inside a Canvas and combines rotation, mirror and zoom in one centered render transform. Only the final aspect viewport clips. Separating rotation into LayoutTransform and mirroring into RenderTransform caused the portrait clipping fixed in Windows 0.8.1.

The loopback OBS page receives unchanged JPEGs through MJPEG and applies EXIF orientation before browser display transforms. Display settings never re-encode the source. Meeting apps currently use OBS Virtual Camera; a standalone Windows virtual camera is not implemented.

The receiver's local control and OBS HTTP routes use port 8787; the phone's TLS endpoint uses 8788. Pairing is scoped to one phone and can be revoked. Closing the desktop stops the receiver; backgrounding Android stops capture. There is no hosted streaming backend, account, telemetry or recording service.

4K, hardware video encoding, audio, dual-camera Studio mode, blur and independent virtual camera output are planned. Audio needs synchronized transport/playback; concurrent cameras need capability checks and an efficient codec before offering two feeds.

The product website is a separate project and deployment. It distributes release files and is not part of the camera transport.
