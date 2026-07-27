# Security Policy

## Supported versions

Security fixes are normally applied to the latest published release and the
current default branch.

## Reporting a vulnerability

Do not open a public issue for vulnerabilities that expose:

- VPN or subscription keys
- server credentials
- signing keystores or passwords
- private API endpoints
- user-identifying logs

Use GitHub's private vulnerability reporting feature when it is enabled.
Otherwise, contact the repository owner privately.

Include the affected version, reproduction steps, impact, and a sanitized
proof of concept. Remove all real credentials before sharing logs.

## Secret handling

The Android signing keystore is never committed to this repository. GitHub
Actions expects signing data only through encrypted repository secrets.
