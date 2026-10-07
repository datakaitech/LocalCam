# Building and testing

Use Windows for the WPF receiver. Install .NET 10 SDK, a JDK 17+ (the current development build uses JDK 21), Android SDK 36.1 with build tools, and Node.js for receiver integration tests. Android uses Gradle 8.13, AGP 8.13.2 and CameraX 1.4.2.

Set ANDROID_HOME to your Android SDK, or create ignored android/local.properties with your own sdk.dir. Set JAVA_HOME to your JDK. The Gradle wrapper is included; local tools/ is optional and excluded from source control.

```powershell
dotnet build desktop/LocalCam.Desktop.csproj
dotnet run --project desktop/LocalCam.Desktop.csproj
cd android
.\gradlew.bat assembleDebug
.\gradlew.bat bundleRelease lintDebug
cd ..
```

Debug APK: android/app/build/outputs/apk/debug/app-debug.apk. The release bundle is unsigned; configure release signing privately before distribution. No production keys are checked in. On another PC, framework-dependent Windows binaries require both .NET 10 Desktop and ASP.NET Core runtimes.

```powershell
.\scripts\test-video.ps1 -JdkPath 'YOUR_JDK_DIRECTORY'
node scripts/test-output.mjs
node scripts/test-stream.mjs
node scripts/test-security.mjs
```

The video script generates ignored EXIF fixtures from scripts/tests/landscape.jpg and runs Java core checks plus the WPF rendering regression. Receiver tests start/stop a temporary headless process on ports 8787/8788; close the desktop first. Synthetic checks do not establish sustained phone FPS or live OBS behaviour. Report actual-device results separately.

```powershell
.\scripts\package-release.ps1
```

Packaging writes Windows, Android and source downloads plus checksums under ignored artifacts/releases/. Build Android first. Downloads belong in release assets, not Git source. To update a separately maintained local site, pass -WebsiteRoot with its actual web root; this copies release files only and does not deploy.

In the current development workspace the active website root is ../localcam-website/website. The legacy website/ copy in this application folder is ignored and no longer maintained. Website checks can use LOCALCAM_WEBSITE_ROOT or the sibling root with node scripts/test-website.mjs.
