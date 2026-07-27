# GitHub Actions

## Android CI

The CI workflow runs Android lint, debug unit tests, and a debug APK build.
Artifacts are retained for a limited time and can be downloaded from the
workflow run.

## Android Release

Push a semantic version tag:

```bash
git tag -a v1.0.0 -m "VicoVPN v1.0.0"
git push origin v1.0.0
```

The release workflow builds APK assets, creates SHA-256 checksums, and creates
or updates the corresponding GitHub Release.

## Wiki sync

Wiki pages are maintained in `docs/wiki`. The Wiki workflow requires the
`WIKI_TOKEN` secret and an initialized GitHub Wiki repository.
