# Device test checklist

Run these checks on at least one physical arm64 Android device before distributing a
production APK.

## Installation and lifecycle

- Grant the Android VPN permission and notification permission.
- Connect and disconnect at least ten times.
- Revoke VPN permission from Android settings while connected.
- Lock/unlock the device and leave the app backgrounded for at least five minutes.
- Switch Wi-Fi to cellular and cellular to Wi-Fi.
- Verify that no stale VPN key, notification, process, or local port remains after stop.

## Routing

- Open an HTTPS site in two unrelated third-party applications.
- Verify TCP and UDP traffic.
- Compare the measured exit IP with the proxy server's expected egress.
- Run an IPv4 and IPv6 leak test.
- Run a DNS leak test and confirm the ISP resolver is not used for tunneled traffic.
- Confirm that an unreachable server moves the UI to Error rather than Connected.

## Protocol matrix

Test only with profiles you are authorized to use:

- VMess TCP and WebSocket/TLS
- VLESS TLS and REALITY
- Trojan TLS
- Shadowsocks without plugins
- gRPC, HTTPUpgrade and XHTTP when the server actually supports them

Record Android version, device model, ABI, network type, profile transport, result, and
redacted logs in the release test report.
