# Installation

## GitHub Release

Open the repository's **Releases** page and download the latest APK.

A release can contain:

- `VicoVPN-vX.Y.Z.apk`: production APK signed by the repository owner
- `VicoVPN-vX.Y.Z-debug.apk`: installable debug APK produced by CI
- `checksums-sha256.txt`: SHA-256 hashes for release assets

Android may require permission to install apps from the browser or file
manager used to open the APK.

## Verify checksum

On PowerShell:

```powershell
Get-FileHash .\VicoVPN-v1.0.0.apk -Algorithm SHA256
```

Compare the result with `checksums-sha256.txt`.
