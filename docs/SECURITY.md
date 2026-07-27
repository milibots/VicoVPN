# Security notes

- VicoVPN intentionally does not provide a certificate-verification bypass.
- Imported links are retained in private app preferences; diagnostics redact schemes,
  UUIDs and password-like fields.
- Android loopback is shared by apps on many releases. The internal SOCKS listener is
  bound only to `127.0.0.1` and receives a fresh random high port for each connection.
  It has no authentication because Java's standard URL stack does not expose SOCKS5
  username/password negotiation; the random port reduces opportunistic local access.
- Public IP is accepted only from an HTTPS response and rejected if loopback, private,
  link-local, multicast or carrier-grade NAT.
- The app does not fabricate geography or ASN data.
