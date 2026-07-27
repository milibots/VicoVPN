# GitHub setup for VicoVPN

This repository is prepared for automated Android builds, release APK assets,
dependency updates, issue templates, and Wiki synchronization.

## Generated automation

### Android CI

`.github/workflows/android-ci.yml` runs on pushes and pull requests. It uses
JDK 17, the Gradle wrapper, Gradle build caching, Android lint, unit tests, and
`assembleDebug`. The resulting APK and reports are uploaded as workflow
artifacts.

### Android releases

`.github/workflows/android-release.yml` runs for tags beginning with `v`, such
as `v1.0.0`. It always publishes a verified debug APK. When Android signing
secrets exist, it also builds, aligns, signs, and verifies a production APK.

Required signing secrets:

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded `.jks` or `.keystore` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

PowerShell example for encoding a keystore:

```powershell
[Convert]::ToBase64String(
    [IO.File]::ReadAllBytes("C:\path\to\vicovpn-release.jks")
) | Set-Clipboard
```

Store that clipboard value as `ANDROID_KEYSTORE_BASE64`. Never commit the
keystore itself.

### Wiki synchronization

Documentation is stored under `docs/wiki`. GitHub Wiki uses a separate Git
repository. First enable Wiki in repository settings and create the initial
Home page once.

For automatic synchronization, create a repository secret named `WIKI_TOKEN`.
Use a token that can write to the repository Wiki. The workflow then copies
`docs/wiki` to the Wiki repository whenever those files change.

## First repository push

Install and authenticate GitHub CLI:

```powershell
gh auth login
```

Then run:

```powershell
python github_setup.py --repo OWNER/VicoVPN --visibility private --push
```

## First release

After the repository is pushed:

```powershell
python github_setup.py --repo OWNER/VicoVPN --push --create-release v0.1.0
```

Pushing the tag starts the Android Release workflow. The release page is
created after the workflow succeeds.

## Branch protection recommendation

Protect `main` and require the `Lint, test, and build debug APK` check before
merging. Also require pull requests and prevent force pushes.

## License

This setup intentionally does not select a software license. Add a license
only after choosing the legal terms under which the source may be used.
