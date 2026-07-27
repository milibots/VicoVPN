# Build verification

## Verified in the generation environment

- Project file inventory and XML well-formedness.
- Pure Kotlin compilation of the share-link parser, JSON codec and Xray config builder.
- Smoke generation and reparsing of a VMess/Xray TUN configuration.
- Extended parsing/config smoke test covering VMess, VLESS REALITY, Trojan, Shadowsocks, unsupported transports and plugin rejection.
- ZIP CRC/integrity validation.

## Not verified in the generation environment

The generation container has no Android SDK and blocks direct artifact downloads, so an
Android APK build and physical-device tunnel test could not be executed here. The included
GitHub Actions workflow performs the SDK setup, downloads the pinned Xray AAR, runs unit
tests/lint and builds the debug APK in a normal networked CI environment.

Do not interpret source-level smoke verification as a physical-device VPN certification.
Test Wi-Fi, cellular, DNS, IPv4, IPv6 and at least two third-party apps before production.
