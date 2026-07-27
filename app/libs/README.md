# Native Xray AAR

Run `../../scripts/bootstrap-native.sh` (Linux/macOS) or
`../../scripts/bootstrap-native.ps1` (Windows) to download the pinned
`AndroidLibXrayLite` AAR into this directory.

The application source compiles without the AAR because the adapter is reflection-based,
but a runtime VPN connection requires `libv2ray.aar`.
