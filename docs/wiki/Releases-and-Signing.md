# Releases and signing

## Version tags

Use tags such as:

- `v1.0.0`
- `v1.1.0`
- `v2.0.0-beta.1`

## Signing secrets

The release workflow supports these encrypted repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Without all four secrets, the workflow still publishes a debug APK. With all
four, it additionally publishes an aligned and signed production APK.

Never upload a keystore to source control, an issue, an Actions artifact, or a
Wiki page.
