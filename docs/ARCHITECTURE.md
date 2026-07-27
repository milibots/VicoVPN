# Architecture

```text
Other Android applications
  -> Android default routes (0.0.0.0/0 and ::/0)
  -> VpnService TUN file descriptor
  -> Xray TUN inbound
  -> Xray routing rule
  -> selected VMess/VLESS/Trojan/Shadowsocks outbound
  -> remote proxy server
  -> public internet
```

VicoVPN's own package is excluded from `VpnService` because the Xray native core lives
inside that package. This is the loop-prevention mechanism. The UI does not use direct
networking to infer the proxy IP: `ExitIpChecker` creates a SOCKS `Proxy` targeting
a random high port on `127.0.0.1`, and only an HTTPS response through that proxy can move the state to
`CONNECTED`.

## Startup ordering

1. Validate the share link.
2. Initialize AndroidLibXrayLite.
3. Establish the Android TUN with IPv4/IPv6 default routes and DNS routes.
4. Generate a redacted-safe Xray JSON configuration.
5. Call `CoreController.startLoop(config, tunFd)`.
6. Wait for the local SOCKS listener.
7. Query a public-IP HTTPS endpoint through SOCKS.
8. Enter Connected and start reading native traffic counters.

## Shutdown ordering

1. Stop monitoring.
2. Stop Xray.
3. Close the TUN file descriptor.
4. Remove the foreground notification.
5. Publish Disconnected.
