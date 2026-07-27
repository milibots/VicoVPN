# VicoVPN

<!-- github-automation:badges:start -->
[![Android CI](https://github.com/milibots/VicoVPN/actions/workflows/android-ci.yml/badge.svg)](https://github.com/milibots/VicoVPN/actions/workflows/android-ci.yml)
[![Android Release](https://github.com/milibots/VicoVPN/actions/workflows/android-release.yml/badge.svg)](https://github.com/milibots/VicoVPN/actions/workflows/android-release.yml)
[![Latest release](https://img.shields.io/github/v/release/milibots/VicoVPN?display_name=tag&sort=semver)](https://github.com/milibots/VicoVPN/releases/latest)
<!-- github-automation:badges:end -->


A focused Persian Android VPN client using Android `VpnService` and the Xray-core
Android library. It supports importing VMess, VLESS, Trojan and Shadowsocks links.

## What is real in this implementation

- Installs Android default IPv4 and IPv6 routes into a TUN interface.
- Passes the live TUN file descriptor to Xray's native TUN inbound.
- Prevents a routing loop by excluding only VicoVPN's own process.
- Forces the post-connect exit-IP request through a per-connection random Xray loopback SOCKS port.
- Reports Connected only after Xray is running, its SOCKS port is reachable and a
  tunneled HTTPS request returns a valid public IP.
- Never synthesizes an IP, city, ISP or traffic counter.
- Reads upload/download counters from Xray's stats API.

## Build

Prerequisites: Android Studio, JDK 17+ (JDK 21 recommended), Android SDK 35, curl.

```bash
bash scripts/verify-project.sh
bash scripts/bootstrap-native.sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The custom `gradlew` securely downloads Gradle 8.13 and checks the official SHA-256.
Android Studio may also import the project directly.

## Native dependency

`scripts/bootstrap-native.sh` pins AndroidLibXrayLite `v26.6.27`, which bundles
Xray-core `v26.6.27`. The downloaded AAR is intentionally not embedded in
this source ZIP because it is roughly 59 MB and is independently licensed LGPL-3.0.
The CI workflow fetches it before building.

## Important runtime note

A build without `app/libs/libv2ray.aar` opens normally and can validate profiles, but
it refuses to connect and displays an explicit native-library error. Run the bootstrap
script before installing a VPN-capable APK.

## Security

- TLS verification is not disabled.
- No `allowInsecure` switch is generated.
- Proxy credentials and links are redacted from diagnostics.
- The SOCKS endpoint listens only on `127.0.0.1`.
- IPv6 is routed into the tunnel rather than silently leaking.

## License

VicoVPN source is GPL-3.0-or-later. AndroidLibXrayLite is LGPL-3.0. Xray-core uses
MPL-2.0. See `THIRD_PARTY_NOTICES.md` and `docs/ARCHITECTURE.md`.

## Verification boundary

The ZIP includes source verification and CI automation, but it is not a substitute for a
physical-device VPN test. Follow `docs/DEVICE_TEST_CHECKLIST.md` before publishing.
