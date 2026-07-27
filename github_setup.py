#!/usr/bin/env python3
r"""
Prepare an Android project for GitHub hosting, GitHub Actions auto-builds,
tag-driven releases, release APK assets, and a synchronized GitHub Wiki.

Run from the Android project root, for example:
    C:\AndroidProjects\VicoVPN

Basic setup:
    python github_setup.py

Create/push a GitHub repository:
    python github_setup.py --repo OWNER/VicoVPN --visibility private --push

Create/push and publish the staged wiki:
    python github_setup.py --repo OWNER/VicoVPN --visibility private --push --publish-wiki

Trigger the first automated release:
    python github_setup.py --repo OWNER/VicoVPN --push --create-release v0.1.0

Validate the Android debug build locally:
    python github_setup.py --validate

Rollback the latest setup:
    python github_setup.py --rollback

Python 3.8+ compatible.
"""

from pathlib import Path
import argparse
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import xml.etree.ElementTree as ET
from urllib.parse import urlparse


ROOT = Path.cwd()
APP = ROOT / "app"

GITHUB = ROOT / ".github"
WORKFLOWS = GITHUB / "workflows"
ISSUE_TEMPLATES = GITHUB / "ISSUE_TEMPLATE"
WIKI_DOCS = ROOT / "docs" / "wiki"

CI_WORKFLOW = WORKFLOWS / "android-ci.yml"
RELEASE_WORKFLOW = WORKFLOWS / "android-release.yml"
WIKI_WORKFLOW = WORKFLOWS / "wiki-sync.yml"
RELEASE_CONFIG = GITHUB / "release.yml"
DEPENDABOT = GITHUB / "dependabot.yml"
BUG_TEMPLATE = ISSUE_TEMPLATES / "bug_report.yml"
FEATURE_TEMPLATE = ISSUE_TEMPLATES / "feature_request.yml"
ISSUE_CONFIG = ISSUE_TEMPLATES / "config.yml"
PR_TEMPLATE = GITHUB / "pull_request_template.md"

SETUP_GUIDE = ROOT / "GITHUB_SETUP.md"
SECURITY = ROOT / "SECURITY.md"
CONTRIBUTING = ROOT / "CONTRIBUTING.md"
CHANGELOG = ROOT / "CHANGELOG.md"
GITIGNORE = ROOT / ".gitignore"
GITATTRIBUTES = ROOT / ".gitattributes"
EDITORCONFIG = ROOT / ".editorconfig"
README = ROOT / "README.md"

BACKUP_ROOT = ROOT / ".github_setup_backups"
STATE_FILE = ROOT / ".github_setup_state.json"

MANAGED_BADGES_START = "<!-- github-automation:badges:start -->"
MANAGED_BADGES_END = "<!-- github-automation:badges:end -->"


ANDROID_CI_YAML = r"""name: Android CI

on:
  push:
    branches:
      - main
      - master
      - develop
  pull_request:
    branches:
      - main
      - master
      - develop
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: android-ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-debug:
    name: Lint, test, and build debug APK
    runs-on: ubuntu-latest
    timeout-minutes: 35

    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make Gradle wrapper executable
        run: chmod +x ./gradlew

      - name: Validate Gradle wrapper
        run: ./gradlew --version

      - name: Lint, unit test, and build
        run: |
          ./gradlew \
            clean \
            lintDebug \
            testDebugUnitTest \
            assembleDebug \
            --stacktrace \
            --no-daemon

      - name: Prepare APK artifact
        if: success()
        shell: bash
        run: |
          set -euo pipefail
          mkdir -p dist
          APK_PATH="$(find app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)"
          if [[ -z "${APK_PATH}" ]]; then
            echo "Debug APK was not found."
            exit 1
          fi
          cp "${APK_PATH}" "dist/VicoVPN-debug-${GITHUB_SHA::7}.apk"
          (
            cd dist
            sha256sum *.apk > checksums-sha256.txt
          )

      - name: Upload debug APK
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: VicoVPN-debug-${{ github.sha }}
          path: |
            dist/*.apk
            dist/checksums-sha256.txt
          if-no-files-found: error
          retention-days: 14

      - name: Upload lint and test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-reports-${{ github.sha }}
          path: |
            app/build/reports/**
            app/build/test-results/**
          if-no-files-found: ignore
          retention-days: 14
"""


ANDROID_RELEASE_YAML = r"""name: Android Release

on:
  push:
    tags:
      - "v*"
  workflow_dispatch:
    inputs:
      version:
        description: "Release tag, for example v1.0.0"
        required: true
        default: "v0.1.0"
      prerelease:
        description: "Mark this release as a prerelease"
        required: true
        type: boolean
        default: false
      draft:
        description: "Create the release as a draft"
        required: true
        type: boolean
        default: false

permissions:
  contents: write

concurrency:
  group: android-release-${{ github.ref_name || inputs.version }}
  cancel-in-progress: false

jobs:
  release:
    name: Build and publish APK assets
    runs-on: ubuntu-latest
    timeout-minutes: 45

    env:
      ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
      ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
      ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
      ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}

    steps:
      - name: Check out repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Resolve release version
        id: version
        shell: bash
        run: |
          set -euo pipefail
          if [[ "${GITHUB_REF_TYPE}" == "tag" ]]; then
            VERSION="${GITHUB_REF_NAME}"
          else
            VERSION="${{ inputs.version }}"
          fi

          if [[ ! "${VERSION}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]]; then
            echo "Version must look like v1.2.3 or v1.2.3-beta.1."
            exit 1
          fi

          echo "version=${VERSION}" >> "${GITHUB_OUTPUT}"
          echo "Resolved version: ${VERSION}"

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make Gradle wrapper executable
        run: chmod +x ./gradlew

      - name: Build verified debug APK
        shell: bash
        run: |
          set -euo pipefail
          ./gradlew \
            clean \
            lintDebug \
            testDebugUnitTest \
            assembleDebug \
            --stacktrace \
            --no-daemon

          mkdir -p dist
          DEBUG_APK="$(find app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)"
          if [[ -z "${DEBUG_APK}" ]]; then
            echo "Debug APK was not found."
            exit 1
          fi

          cp "${DEBUG_APK}" \
            "dist/VicoVPN-${{ steps.version.outputs.version }}-debug.apk"

      - name: Build and sign production release APK
        id: signed_release
        shell: bash
        run: |
          set -euo pipefail

          if [[ -z "${ANDROID_KEYSTORE_BASE64}" ]] ||
             [[ -z "${ANDROID_KEYSTORE_PASSWORD}" ]] ||
             [[ -z "${ANDROID_KEY_ALIAS}" ]] ||
             [[ -z "${ANDROID_KEY_PASSWORD}" ]]; then
            echo "Signing secrets are incomplete."
            echo "The installable debug APK will still be published."
            echo "created=false" >> "${GITHUB_OUTPUT}"
            exit 0
          fi

          KEYSTORE_PATH="${RUNNER_TEMP}/vicovpn-release.jks"
          echo "${ANDROID_KEYSTORE_BASE64}" | base64 --decode > "${KEYSTORE_PATH}"

          ./gradlew \
            assembleRelease \
            --stacktrace \
            --no-daemon

          UNSIGNED_APK="$(
            find app/build/outputs/apk/release \
              -type f \
              \( -name '*-unsigned.apk' -o -name '*release*.apk' \) \
              | head -n 1
          )"

          if [[ -z "${UNSIGNED_APK}" ]]; then
            echo "Release APK was not found."
            exit 1
          fi

          BUILD_TOOLS="$(
            find "${ANDROID_HOME}/build-tools" \
              -mindepth 1 \
              -maxdepth 1 \
              -type d \
              | sort -V \
              | tail -n 1
          )"

          if [[ -z "${BUILD_TOOLS}" ]]; then
            echo "Android build-tools were not found."
            exit 1
          fi

          ALIGNED_APK="${RUNNER_TEMP}/VicoVPN-aligned.apk"
          SIGNED_APK="dist/VicoVPN-${{ steps.version.outputs.version }}.apk"

          "${BUILD_TOOLS}/zipalign" \
            -p \
            -f \
            4 \
            "${UNSIGNED_APK}" \
            "${ALIGNED_APK}"

          "${BUILD_TOOLS}/apksigner" sign \
            --ks "${KEYSTORE_PATH}" \
            --ks-key-alias "${ANDROID_KEY_ALIAS}" \
            --ks-pass "pass:${ANDROID_KEYSTORE_PASSWORD}" \
            --key-pass "pass:${ANDROID_KEY_PASSWORD}" \
            --out "${SIGNED_APK}" \
            "${ALIGNED_APK}"

          "${BUILD_TOOLS}/apksigner" verify \
            --verbose \
            --print-certs \
            "${SIGNED_APK}"

          echo "created=true" >> "${GITHUB_OUTPUT}"

      - name: Generate checksums
        shell: bash
        run: |
          set -euo pipefail
          (
            cd dist
            sha256sum *.apk > checksums-sha256.txt
          )

      - name: Upload workflow artifacts
        uses: actions/upload-artifact@v4
        with:
          name: VicoVPN-${{ steps.version.outputs.version }}
          path: |
            dist/*.apk
            dist/checksums-sha256.txt
          if-no-files-found: error
          retention-days: 30

      - name: Create or update GitHub Release
        env:
          GH_TOKEN: ${{ github.token }}
          VERSION: ${{ steps.version.outputs.version }}
          MANUAL_DRAFT: ${{ inputs.draft }}
          MANUAL_PRERELEASE: ${{ inputs.prerelease }}
        shell: bash
        run: |
          set -euo pipefail

          EXTRA_FLAGS=()

          if [[ "${GITHUB_EVENT_NAME}" == "workflow_dispatch" ]]; then
            if [[ "${MANUAL_DRAFT}" == "true" ]]; then
              EXTRA_FLAGS+=(--draft)
            fi

            if [[ "${MANUAL_PRERELEASE}" == "true" ]]; then
              EXTRA_FLAGS+=(--prerelease)
            fi
          fi

          if gh release view "${VERSION}" >/dev/null 2>&1; then
            gh release upload \
              "${VERSION}" \
              dist/* \
              --clobber
          else
            gh release create \
              "${VERSION}" \
              dist/* \
              --title "VicoVPN ${VERSION}" \
              --generate-notes \
              --target "${GITHUB_SHA}" \
              "${EXTRA_FLAGS[@]}"
          fi
"""


WIKI_SYNC_YAML = r"""name: Sync GitHub Wiki

on:
  push:
    branches:
      - main
      - master
    paths:
      - "docs/wiki/**"
      - ".github/workflows/wiki-sync.yml"
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: wiki-sync-${{ github.repository }}
  cancel-in-progress: true

jobs:
  sync:
    name: Publish docs/wiki to repository Wiki
    runs-on: ubuntu-latest
    timeout-minutes: 10

    env:
      WIKI_TOKEN: ${{ secrets.WIKI_TOKEN }}

    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Validate Wiki token
        shell: bash
        run: |
          if [[ -z "${WIKI_TOKEN}" ]]; then
            echo "Repository secret WIKI_TOKEN is missing."
            echo "Create a classic PAT with repo access, then save it as WIKI_TOKEN."
            exit 1
          fi

      - name: Clone Wiki repository
        shell: bash
        run: |
          set -euo pipefail
          WIKI_URL="https://x-access-token:${WIKI_TOKEN}@github.com/${GITHUB_REPOSITORY}.wiki.git"

          if ! git clone "${WIKI_URL}" wiki-repository; then
            echo "The Wiki repository could not be cloned."
            echo "Enable Wiki in repository settings and create the first Home page once."
            exit 1
          fi

      - name: Synchronize Wiki pages
        shell: bash
        run: |
          set -euo pipefail
          rsync \
            -a \
            --delete \
            --exclude ".git" \
            docs/wiki/ \
            wiki-repository/

      - name: Commit and push Wiki changes
        working-directory: wiki-repository
        shell: bash
        run: |
          set -euo pipefail

          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"

          git add --all

          if git diff --cached --quiet; then
            echo "Wiki is already up to date."
            exit 0
          fi

          git commit -m "docs: synchronize repository wiki"
          git push origin HEAD
"""


RELEASE_CONFIG_YAML = r"""changelog:
  exclude:
    labels:
      - ignore-for-release
      - documentation
    authors:
      - dependabot

  categories:
    - title: "Breaking changes"
      labels:
        - breaking-change

    - title: "New features"
      labels:
        - enhancement
        - feature

    - title: "Fixes"
      labels:
        - bug
        - fix

    - title: "Performance and stability"
      labels:
        - performance
        - stability

    - title: "Other changes"
      labels:
        - "*"
"""


DEPENDABOT_YAML = r"""version: 2

updates:
  - package-ecosystem: gradle
    directory: "/"
    schedule:
      interval: weekly
      day: monday
      time: "06:00"
    open-pull-requests-limit: 5
    labels:
      - dependencies
      - android

  - package-ecosystem: github-actions
    directory: "/"
    schedule:
      interval: weekly
      day: monday
      time: "06:30"
    open-pull-requests-limit: 5
    labels:
      - dependencies
      - github-actions
"""


BUG_TEMPLATE_YAML = r"""name: Bug report
description: Report a reproducible problem in VicoVPN
title: "[Bug]: "
labels:
  - bug
body:
  - type: markdown
    attributes:
      value: |
        Thanks for helping improve VicoVPN. Do not include VPN keys,
        subscription tokens, private server addresses, or keystore data.

  - type: input
    id: version
    attributes:
      label: App version
      description: Release tag, commit, or APK version.
      placeholder: v1.0.0
    validations:
      required: true

  - type: input
    id: device
    attributes:
      label: Device and Android version
      placeholder: Pixel 8, Android 16
    validations:
      required: true

  - type: dropdown
    id: build
    attributes:
      label: Build type
      options:
        - GitHub release APK
        - GitHub Actions debug artifact
        - Local debug build
        - Local release build
    validations:
      required: true

  - type: textarea
    id: steps
    attributes:
      label: Reproduction steps
      placeholder: |
        1. Open the app
        2. Select...
        3. Tap...
    validations:
      required: true

  - type: textarea
    id: expected
    attributes:
      label: Expected behavior
    validations:
      required: true

  - type: textarea
    id: actual
    attributes:
      label: Actual behavior
    validations:
      required: true

  - type: textarea
    id: logs
    attributes:
      label: Sanitized logs
      description: Remove keys, tokens, addresses, and personal data before posting.
      render: shell
"""


FEATURE_TEMPLATE_YAML = r"""name: Feature request
description: Suggest a product or technical improvement
title: "[Feature]: "
labels:
  - enhancement
body:
  - type: textarea
    id: problem
    attributes:
      label: Problem
      description: What user problem should this solve?
    validations:
      required: true

  - type: textarea
    id: solution
    attributes:
      label: Proposed solution
      description: Describe the desired behavior and interface.
    validations:
      required: true

  - type: textarea
    id: alternatives
    attributes:
      label: Alternatives considered

  - type: checkboxes
    id: safety
    attributes:
      label: Safety check
      options:
        - label: This request does not include private VPN keys, subscription tokens, or credentials.
          required: true
"""


ISSUE_CONFIG_YAML = r"""blank_issues_enabled: false
contact_links:
  - name: Security report
    url: ../../security/policy
    about: Report security vulnerabilities privately.
"""


PR_TEMPLATE_MD = r"""## Summary

Describe what changed and why.

## Validation

- [ ] `./gradlew lintDebug`
- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew assembleDebug`
- [ ] Tested light mode
- [ ] Tested dark mode
- [ ] Tested Persian layout
- [ ] Tested English layout
- [ ] No subscription key, server credential, or signing secret was committed

## Screenshots

Add screenshots for visible UI changes.

## Release impact

- [ ] No release note required
- [ ] Fix
- [ ] Feature
- [ ] Breaking change
"""


GITIGNORE_ENTRIES = r"""
# Android and Gradle
.gradle/
**/build/
local.properties
captures/
.externalNativeBuild/
.cxx/

# Android Studio / IntelliJ
.idea/
*.iml
navigation.xml
assetWizardSettings.xml
deploymentTargetDropDown.xml

# Signing material and secrets
*.jks
*.keystore
*.p12
*.pfx
*.pem
*.key
keystore.properties
signing.properties
secrets.properties
.env
.env.*
!.env.example

# Generated release files
dist/
release/
*.apk
*.aab
mapping.txt

# Local diagnostics and backups
.vicovpn_*_backups/
.github_setup_backups/
.github_setup_state.json
.vicovpn_crash_diagnostics/
vicovpn_*_crash_*.zip
logcat*.txt
crash*.txt

# Operating systems
.DS_Store
Thumbs.db
desktop.ini

# Editors
.vscode/
*.swp
*.swo
"""


GITATTRIBUTES_TEXT = r"""* text=auto

*.kt text eol=lf
*.kts text eol=lf
*.java text eol=lf
*.xml text eol=lf
*.yml text eol=lf
*.yaml text eol=lf
*.md text eol=lf
*.properties text eol=lf

*.png binary
*.jpg binary
*.jpeg binary
*.webp binary
*.gif binary
*.apk binary
*.aab binary
*.jks binary
*.keystore binary
"""


EDITORCONFIG_TEXT = r"""root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts,java}]
indent_style = space
indent_size = 4

[*.{xml,yml,yaml,json,md}]
indent_style = space
indent_size = 2

[*.md]
trim_trailing_whitespace = false
"""


SECURITY_MD = r"""# Security Policy

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
"""


CONTRIBUTING_MD = r"""# Contributing to VicoVPN

## Development requirements

- Android Studio
- JDK 17
- Android SDK configured through `local.properties`
- The repository Gradle wrapper

## Before opening a pull request

Run:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

On Windows:

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

Check both Persian and English layouts, light and dark themes, onboarding,
connection states, server selection, and bottom navigation behavior.

Never commit subscription keys, VPN configurations, keystores, passwords,
private endpoints, or unsanitized logs.
"""


CHANGELOG_MD = r"""# Changelog

All notable project changes should be recorded here.

The release workflow also generates GitHub release notes from merged pull
requests and labels.

## Unreleased

### Added

### Changed

### Fixed

### Security
"""


SETUP_GUIDE_TEMPLATE = r"""# GitHub setup for VicoVPN

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
"""


WIKI_PAGES = {
    "Home.md": r"""# VicoVPN

VicoVPN is an Android VPN client focused on a clean connection experience,
smart route selection, free and premium route priorities, split tunneling,
and Persian/English interface support.

## Start here

- [Installation](Installation)
- [Build from source](Build-from-Source)
- [GitHub Actions](GitHub-Actions)
- [Releases and signing](Releases-and-Signing)
- [Architecture](Architecture)
- [Troubleshooting](Troubleshooting)
- [Privacy and security](Privacy-and-Security)

Do not publish real subscription keys, VPN configurations, private server
addresses, signing material, or unsanitized diagnostic logs.
""",
    "Installation.md": r"""# Installation

## GitHub Release

Open the repository's **Releases** page and download the latest APK.

A release can contain:

- `VicoVPN-vX.Y.Z.apk`: production APK signed by the repository owner
- `VicoVPN-vX.Y.Z-debug.apk`: installable debug APK produced by CI
- `checksums-sha256.txt`: SHA-256 hashes for release assets

Android may require permission to install apps from the browser or file
manager used to open the APK.

## Verify checksum

On PowerShell:

```powershell
Get-FileHash .\VicoVPN-v1.0.0.apk -Algorithm SHA256
```

Compare the result with `checksums-sha256.txt`.
""",
    "Build-from-Source.md": r"""# Build from source

## Requirements

- Android Studio
- JDK 17
- Android SDK
- Git
- The Gradle wrapper included in the repository

## Windows

```powershell
git clone REPOSITORY_URL
cd VicoVPN
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat clean lintDebug testDebugUnitTest assembleDebug --no-daemon
```

The debug APK is normally produced under:

```text
app\build\outputs\apk\debug\
```

## Linux or macOS

```bash
git clone REPOSITORY_URL
cd VicoVPN
chmod +x gradlew
./gradlew clean lintDebug testDebugUnitTest assembleDebug --no-daemon
```

`local.properties` is machine-specific and must not be committed.
""",
    "GitHub-Actions.md": r"""# GitHub Actions

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
""",
    "Releases-and-Signing.md": r"""# Releases and signing

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
""",
    "Architecture.md": r"""# Architecture

The project is organized as a conventional Android application with Kotlin,
XML layouts, Gradle, and Android services.

Main areas include:

- application and activity lifecycle
- VPN service and connection state
- server and route storage
- free subscription discovery and refresh
- premium subscription profile
- connection-priority settings
- split tunneling
- onboarding
- theme, language, and typography
- background workers and notifications

Background tasks must remain lifecycle-safe and must not block the main
thread. UI state should be derived from persisted connection and subscription
state rather than temporary view state.
""",
    "Troubleshooting.md": r"""# Troubleshooting

## CI cannot execute Gradle

Confirm `gradlew` exists and is committed. GitHub Actions runs:

```bash
chmod +x ./gradlew
```

## SDK XML warning

A warning about SDK XML versions usually means Android Studio and command-line
SDK tools were released at different times. Update Android SDK command-line
tools when practical.

## Native library cannot be stripped

Messages about packaging `libgojni.so` without stripping are commonly warnings
rather than build failures. Check the first actual compiler or Gradle error.

## Release contains only a debug APK

Add all four Android signing secrets described in
[Releases and signing](Releases-and-Signing).

## Wiki sync cannot clone

Enable the repository Wiki, create the first Home page once, and confirm that
`WIKI_TOKEN` can write to the Wiki repository.
""",
    "Privacy-and-Security.md": r"""# Privacy and security

Never publish:

- subscription keys or tokens
- complete VPN share links
- UUIDs tied to real subscriptions
- private server IP addresses or domains
- keystores or signing passwords
- API authentication tokens
- unsanitized device logs

When opening an issue, replace sensitive values with obvious placeholders.
GitHub Actions signing credentials must be stored only as encrypted repository
secrets.
""",
    "_Sidebar.md": r"""**VicoVPN Wiki**

- [Home](Home)
- [Installation](Installation)
- [Build from source](Build-from-Source)
- [GitHub Actions](GitHub-Actions)
- [Releases and signing](Releases-and-Signing)
- [Architecture](Architecture)
- [Troubleshooting](Troubleshooting)
- [Privacy and security](Privacy-and-Security)
""",
    "_Footer.md": r"""VicoVPN documentation · Never publish credentials or subscription keys.
""",
}


def fail(message):
    print("\nERROR: " + str(message), file=sys.stderr)
    raise SystemExit(1)


def notice(message):
    print("NOTICE:", message)


def run(command, cwd=None, env=None, check=True, capture=False):
    printable = " ".join(str(part) for part in command)
    print("Running:", printable)

    result = subprocess.run(
        [str(part) for part in command],
        cwd=str(cwd or ROOT),
        env=env,
        check=False,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )

    if check and result.returncode != 0:
        details = ""
        if capture:
            details = "\n" + (result.stderr or result.stdout or "").strip()
        fail("Command failed: " + printable + details)

    return result


def read_text(path):
    return path.read_text(encoding="utf-8-sig")


def write_text(path, content, overwrite=True):
    if path.exists() and not overwrite:
        notice("Kept existing file: " + str(path.relative_to(ROOT)))
        return False

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.rstrip() + "\n", encoding="utf-8")
    print("Updated:", path.relative_to(ROOT))
    return True


def ensure_android_project():
    wrapper_windows = ROOT / "gradlew.bat"
    wrapper_unix = ROOT / "gradlew"

    if not APP.is_dir():
        fail("The app directory was not found. Run this script from the Android project root.")

    if not wrapper_windows.exists() and not wrapper_unix.exists():
        fail("Neither gradlew.bat nor gradlew was found in the project root.")

    manifest = APP / "src" / "main" / "AndroidManifest.xml"
    if not manifest.exists():
        fail("app/src/main/AndroidManifest.xml was not found.")


def collect_managed_paths():
    paths = [
        CI_WORKFLOW,
        RELEASE_WORKFLOW,
        WIKI_WORKFLOW,
        RELEASE_CONFIG,
        DEPENDABOT,
        BUG_TEMPLATE,
        FEATURE_TEMPLATE,
        ISSUE_CONFIG,
        PR_TEMPLATE,
        SETUP_GUIDE,
        SECURITY,
        CONTRIBUTING,
        CHANGELOG,
        GITIGNORE,
        GITATTRIBUTES,
        EDITORCONFIG,
        README,
    ]

    for page_name in WIKI_PAGES:
        paths.append(WIKI_DOCS / page_name)

    return paths


def make_backup(paths):
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = BACKUP_ROOT / stamp
    backup.mkdir(parents=True, exist_ok=False)

    state = {
        "backup": str(backup.relative_to(ROOT)),
        "files": {},
    }

    for path in paths:
        relative = str(path.relative_to(ROOT))
        existed = path.exists()
        state["files"][relative] = {"existed": existed}

        if existed:
            destination = backup / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)

    STATE_FILE.write_text(
        json.dumps(state, indent=2),
        encoding="utf-8",
    )

    return backup


def rollback():
    if not STATE_FILE.exists():
        fail("No github_setup backup state was found.")

    state = json.loads(STATE_FILE.read_text(encoding="utf-8"))
    backup = ROOT / state["backup"]

    for relative, record in state["files"].items():
        target = ROOT / relative
        saved = backup / relative

        if record["existed"]:
            if not saved.exists():
                fail("Backup file is missing: " + str(saved))
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(saved, target)
        elif target.exists():
            if target.is_dir():
                shutil.rmtree(target)
            else:
                target.unlink()

    STATE_FILE.unlink()
    print("Rollback complete:", backup)


def append_unique_block(path, block, marker):
    existing = read_text(path) if path.exists() else ""

    if marker in existing:
        notice("Managed block already exists in " + str(path.relative_to(ROOT)))
        return

    content = existing.rstrip()

    if content:
        content += "\n\n"

    content += block.strip() + "\n"
    write_text(path, content)


def merge_gitignore():
    marker = "# GitHub/Android automation additions"

    if GITIGNORE.exists():
        existing = read_text(GITIGNORE)

        if marker in existing:
            notice(".gitignore already contains the managed block.")
            return

        content = existing.rstrip() + "\n\n" + marker + "\n" + GITIGNORE_ENTRIES.strip() + "\n"
    else:
        content = marker + "\n" + GITIGNORE_ENTRIES.strip() + "\n"

    write_text(GITIGNORE, content)


def normalize_repo_slug(value):
    if not value:
        return None

    value = value.strip()

    ssh_match = re.match(
        r"git@github\.com:([^/]+)/([^/]+?)(?:\.git)?$",
        value,
    )

    if ssh_match:
        return ssh_match.group(1) + "/" + ssh_match.group(2)

    if value.startswith("http://") or value.startswith("https://"):
        parsed = urlparse(value)
        if parsed.netloc.lower() != "github.com":
            return None
        parts = [part for part in parsed.path.split("/") if part]
        if len(parts) >= 2:
            repo = parts[1]
            if repo.endswith(".git"):
                repo = repo[:-4]
            return parts[0] + "/" + repo
        return None

    if re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", value):
        return value

    return None


def infer_repo_slug():
    if not shutil.which("git") or not (ROOT / ".git").exists():
        return None

    result = run(
        ["git", "remote", "get-url", "origin"],
        check=False,
        capture=True,
    )

    if result.returncode != 0:
        return None

    return normalize_repo_slug((result.stdout or "").strip())


def badges_block(repo_slug):
    if not repo_slug:
        return None

    escaped = repo_slug

    return "\n".join([
        MANAGED_BADGES_START,
        "[![Android CI](https://github.com/{0}/actions/workflows/android-ci.yml/badge.svg)](https://github.com/{0}/actions/workflows/android-ci.yml)".format(escaped),
        "[![Android Release](https://github.com/{0}/actions/workflows/android-release.yml/badge.svg)](https://github.com/{0}/actions/workflows/android-release.yml)".format(escaped),
        "[![Latest release](https://img.shields.io/github/v/release/{0}?display_name=tag&sort=semver)](https://github.com/{0}/releases/latest)".format(escaped),
        MANAGED_BADGES_END,
    ])


def patch_readme(repo_slug):
    if not README.exists():
        title = "# VicoVPN\n\nAndroid VPN client.\n"
        write_text(README, title)

    block = badges_block(repo_slug)

    if not block:
        notice("README badges were skipped because no OWNER/REPOSITORY slug was available.")
        return

    text = read_text(README)

    managed_pattern = re.compile(
        re.escape(MANAGED_BADGES_START)
        + r".*?"
        + re.escape(MANAGED_BADGES_END),
        flags=re.DOTALL,
    )

    if managed_pattern.search(text):
        text = managed_pattern.sub(block, text, count=1)
    else:
        lines = text.splitlines()
        insertion_index = 1 if lines and lines[0].startswith("#") else 0
        lines[insertion_index:insertion_index] = ["", block, ""]
        text = "\n".join(lines)

    write_text(README, text)


def write_repository_files(repo_slug):
    write_text(CI_WORKFLOW, ANDROID_CI_YAML)
    write_text(RELEASE_WORKFLOW, ANDROID_RELEASE_YAML)
    write_text(WIKI_WORKFLOW, WIKI_SYNC_YAML)
    write_text(RELEASE_CONFIG, RELEASE_CONFIG_YAML)
    write_text(DEPENDABOT, DEPENDABOT_YAML)
    write_text(BUG_TEMPLATE, BUG_TEMPLATE_YAML)
    write_text(FEATURE_TEMPLATE, FEATURE_TEMPLATE_YAML)
    write_text(ISSUE_CONFIG, ISSUE_CONFIG_YAML)
    write_text(PR_TEMPLATE, PR_TEMPLATE_MD)

    write_text(SETUP_GUIDE, SETUP_GUIDE_TEMPLATE)

    write_text(SECURITY, SECURITY_MD, overwrite=False)
    write_text(CONTRIBUTING, CONTRIBUTING_MD, overwrite=False)
    write_text(CHANGELOG, CHANGELOG_MD, overwrite=False)
    write_text(GITATTRIBUTES, GITATTRIBUTES_TEXT)
    write_text(EDITORCONFIG, EDITORCONFIG_TEXT)

    merge_gitignore()
    patch_readme(repo_slug)

    for page_name, content in WIKI_PAGES.items():
        write_text(WIKI_DOCS / page_name, content)


def validate_xml_files():
    manifest = APP / "src" / "main" / "AndroidManifest.xml"

    try:
        ET.parse(str(manifest))
    except Exception as error:
        fail("AndroidManifest.xml is invalid: " + str(error))


def validate_generated_files():
    required = [
        CI_WORKFLOW,
        RELEASE_WORKFLOW,
        WIKI_WORKFLOW,
        RELEASE_CONFIG,
        DEPENDABOT,
        WIKI_DOCS / "Home.md",
        WIKI_DOCS / "_Sidebar.md",
    ]

    missing = [str(path.relative_to(ROOT)) for path in required if not path.exists()]

    if missing:
        fail("Generated files are missing: " + ", ".join(missing))

    ci = read_text(CI_WORKFLOW)
    release = read_text(RELEASE_WORKFLOW)
    wiki = read_text(WIKI_WORKFLOW)

    required_ci = [
        "actions/checkout@v4",
        "actions/setup-java@v4",
        "gradle/actions/setup-gradle@v4",
        "actions/upload-artifact@v4",
        "assembleDebug",
    ]

    for token in required_ci:
        if token not in ci:
            fail("CI workflow validation failed. Missing: " + token)

    required_release = [
        "permissions:",
        "contents: write",
        "gh release create",
        "apksigner",
        "checksums-sha256.txt",
    ]

    for token in required_release:
        if token not in release:
            fail("Release workflow validation failed. Missing: " + token)

    if "WIKI_TOKEN" not in wiki or ".wiki.git" not in wiki:
        fail("Wiki workflow validation failed.")

    validate_xml_files()


def find_java_home():
    candidates = []

    configured = os.environ.get("JAVA_HOME")
    if configured:
        candidates.append(Path(configured))

    candidates.extend([
        Path(r"C:\Program Files\Android\Android Studio\jbr"),
        Path(r"C:\Program Files\Android\Android Studio\jre"),
    ])

    java_on_path = shutil.which("java")
    if java_on_path:
        candidates.append(Path(java_on_path).resolve().parent.parent)

    executable = "java.exe" if os.name == "nt" else "java"

    for candidate in candidates:
        if (candidate / "bin" / executable).is_file():
            return candidate

    return None


def validate_android_build():
    java_home = find_java_home()

    if java_home is None:
        fail("Java was not found. Set JAVA_HOME to Android Studio's jbr directory.")

    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java_home)
    environment["PATH"] = (
        str(java_home / "bin")
        + os.pathsep
        + environment.get("PATH", "")
    )

    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")

    if os.name != "nt":
        wrapper.chmod(wrapper.stat().st_mode | 0o111)

    run(
        [
            str(wrapper),
            "clean",
            "lintDebug",
            "testDebugUnitTest",
            "assembleDebug",
            "--stacktrace",
            "--no-daemon",
        ],
        env=environment,
    )

    apk_candidates = list(
        (APP / "build" / "outputs" / "apk" / "debug").glob("*.apk")
    )

    if not apk_candidates:
        fail("Gradle succeeded, but no debug APK was found.")

    print("APK:", apk_candidates[0])


def ensure_git_identity():
    name = run(
        ["git", "config", "--get", "user.name"],
        check=False,
        capture=True,
    )

    email = run(
        ["git", "config", "--get", "user.email"],
        check=False,
        capture=True,
    )

    if name.returncode != 0 or not (name.stdout or "").strip():
        run(["git", "config", "user.name", "VicoVPN Maintainer"])

    if email.returncode != 0 or not (email.stdout or "").strip():
        run([
            "git",
            "config",
            "user.email",
            "vicovpn-maintainer@users.noreply.github.com",
        ])


def ensure_git_repository(default_branch):
    if not shutil.which("git"):
        fail("Git is not installed or is not available in PATH.")

    if not (ROOT / ".git").exists():
        run(["git", "init"])
        run(["git", "branch", "-M", default_branch])

    ensure_git_identity()


def ensure_gh_authenticated():
    if not shutil.which("gh"):
        fail("GitHub CLI is required for --push or --publish-wiki. Install gh and run 'gh auth login'.")

    result = run(
        ["gh", "auth", "status"],
        check=False,
        capture=True,
    )

    if result.returncode != 0:
        fail("GitHub CLI is not authenticated. Run: gh auth login")


def repository_exists(repo_slug):
    result = run(
        ["gh", "repo", "view", repo_slug],
        check=False,
        capture=True,
    )
    return result.returncode == 0


def ensure_remote(repo_slug, visibility, description):
    if repository_exists(repo_slug):
        notice("GitHub repository already exists: " + repo_slug)
    else:
        command = [
            "gh",
            "repo",
            "create",
            repo_slug,
            "--source",
            ".",
            "--remote",
            "origin",
            "--description",
            description,
        ]

        if visibility == "public":
            command.append("--public")
        elif visibility == "internal":
            command.append("--internal")
        else:
            command.append("--private")

        run(command)

    remotes = run(
        ["git", "remote"],
        capture=True,
    ).stdout.split()

    expected_url = "https://github.com/" + repo_slug + ".git"

    if "origin" not in remotes:
        run(["git", "remote", "add", "origin", expected_url])
    else:
        current = run(
            ["git", "remote", "get-url", "origin"],
            capture=True,
        ).stdout.strip()

        current_slug = normalize_repo_slug(current)

        if current_slug and current_slug.lower() != repo_slug.lower():
            fail(
                "The existing origin remote points to {0}, not {1}. "
                "Change it manually or use the matching --repo value.".format(
                    current_slug,
                    repo_slug,
                )
            )

    run([
        "gh",
        "repo",
        "edit",
        repo_slug,
        "--enable-issues",
        "--enable-wiki",
        "--delete-branch-on-merge",
    ])


def commit_and_push(default_branch):
    run(["git", "branch", "-M", default_branch])
    run(["git", "add", "--all"])

    diff = run(
        ["git", "diff", "--cached", "--quiet"],
        check=False,
    )

    if diff.returncode != 0:
        run([
            "git",
            "commit",
            "-m",
            "chore: add GitHub builds, releases, and wiki docs",
        ])
    else:
        notice("No new Git changes required a commit.")

    run(["git", "push", "-u", "origin", default_branch])


def create_release_tag(tag, default_branch):
    if not re.fullmatch(
        r"v[0-9]+\.[0-9]+\.[0-9]+(?:[.-][0-9A-Za-z.-]+)?",
        tag,
    ):
        fail("Release tag must look like v1.2.3 or v1.2.3-beta.1.")

    existing = run(
        ["git", "tag", "--list", tag],
        capture=True,
    ).stdout.strip()

    if not existing:
        run([
            "git",
            "tag",
            "-a",
            tag,
            "-m",
            "VicoVPN " + tag,
        ])

    run(["git", "push", "origin", tag])

    print(
        "Release tag pushed. GitHub Actions will create the release page "
        "after Android Release succeeds."
    )


def publish_wiki(repo_slug):
    ensure_gh_authenticated()

    run([
        "gh",
        "repo",
        "edit",
        repo_slug,
        "--enable-wiki",
    ])

    wiki_url = "https://github.com/" + repo_slug + ".wiki.git"

    with tempfile.TemporaryDirectory(prefix="vicovpn-wiki-") as temp_name:
        temp_dir = Path(temp_name)
        clone_dir = temp_dir / "wiki"

        result = run(
            ["git", "clone", wiki_url, str(clone_dir)],
            check=False,
            capture=True,
        )

        if result.returncode != 0:
            fail(
                "The Wiki Git repository is not initialized yet. "
                "Open the repository's Wiki tab, create the first Home page once, "
                "then rerun with --publish-wiki."
            )

        for child in clone_dir.iterdir():
            if child.name == ".git":
                continue
            if child.is_dir():
                shutil.rmtree(child)
            else:
                child.unlink()

        for source in WIKI_DOCS.iterdir():
            destination = clone_dir / source.name
            if source.is_dir():
                shutil.copytree(source, destination)
            else:
                shutil.copy2(source, destination)

        run(["git", "config", "user.name", "VicoVPN Maintainer"], cwd=clone_dir)
        run([
            "git",
            "config",
            "user.email",
            "vicovpn-maintainer@users.noreply.github.com",
        ], cwd=clone_dir)

        run(["git", "add", "--all"], cwd=clone_dir)

        diff = run(
            ["git", "diff", "--cached", "--quiet"],
            cwd=clone_dir,
            check=False,
        )

        if diff.returncode == 0:
            notice("The GitHub Wiki is already up to date.")
            return

        run(
            ["git", "commit", "-m", "docs: publish VicoVPN wiki"],
            cwd=clone_dir,
        )
        run(["git", "push", "origin", "HEAD"], cwd=clone_dir)

    print("Wiki published:", "https://github.com/" + repo_slug + "/wiki")


def print_next_steps(repo_slug):
    print("\nGitHub setup files are ready.")

    if repo_slug:
        print("Repository:", "https://github.com/" + repo_slug)
        print("Actions:", "https://github.com/" + repo_slug + "/actions")
        print("Releases:", "https://github.com/" + repo_slug + "/releases")
        print("Wiki:", "https://github.com/" + repo_slug + "/wiki")
    else:
        print(
            "To create and push a repository, rerun with:\n"
            "  python github_setup.py --repo OWNER/VicoVPN --visibility private --push"
        )

    print(
        "\nFor production-signed APK releases, add these repository secrets:\n"
        "  ANDROID_KEYSTORE_BASE64\n"
        "  ANDROID_KEYSTORE_PASSWORD\n"
        "  ANDROID_KEY_ALIAS\n"
        "  ANDROID_KEY_PASSWORD"
    )

    print(
        "\nFor automatic Wiki synchronization, initialize the Wiki once and add:\n"
        "  WIKI_TOKEN"
    )


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Prepare and optionally publish the VicoVPN Android repository "
            "with GitHub Actions, releases, and Wiki documentation."
        )
    )

    parser.add_argument(
        "--repo",
        help="GitHub repository as OWNER/REPOSITORY.",
    )

    parser.add_argument(
        "--visibility",
        choices=["private", "public", "internal"],
        default="private",
        help="Visibility used when creating a new GitHub repository.",
    )

    parser.add_argument(
        "--description",
        default=(
            "VicoVPN Android client with smart free and premium route selection."
        ),
        help="Description for a newly created GitHub repository.",
    )

    parser.add_argument(
        "--default-branch",
        default="main",
        help="Default branch to create and push.",
    )

    parser.add_argument(
        "--push",
        action="store_true",
        help="Create/configure the GitHub repository, commit, and push.",
    )

    parser.add_argument(
        "--publish-wiki",
        action="store_true",
        help="Push docs/wiki to the initialized GitHub Wiki repository.",
    )

    parser.add_argument(
        "--create-release",
        metavar="TAG",
        help="Create and push a tag such as v0.1.0 after pushing the repository.",
    )

    parser.add_argument(
        "--validate",
        action="store_true",
        help="Run lintDebug, testDebugUnitTest, and assembleDebug locally.",
    )

    parser.add_argument(
        "--rollback",
        action="store_true",
        help="Restore files changed by the latest github_setup run.",
    )

    args = parser.parse_args()

    ensure_android_project()

    if args.rollback:
        rollback()
        return

    explicit_slug = normalize_repo_slug(args.repo)

    if args.repo and not explicit_slug:
        fail("--repo must look like OWNER/REPOSITORY.")

    inferred_slug = infer_repo_slug()
    repo_slug = explicit_slug or inferred_slug

    if (args.push or args.publish_wiki or args.create_release) and not repo_slug:
        fail("--repo OWNER/REPOSITORY is required for publishing operations.")

    managed_paths = collect_managed_paths()
    backup = make_backup(managed_paths)
    print("Backup:", backup)

    write_repository_files(repo_slug)
    validate_generated_files()

    if args.validate:
        validate_android_build()

    if args.push:
        ensure_git_repository(args.default_branch)
        ensure_gh_authenticated()
        ensure_remote(
            repo_slug,
            args.visibility,
            args.description,
        )
        commit_and_push(args.default_branch)

    if args.create_release:
        if not args.push:
            fail("--create-release requires --push so the repository is synchronized first.")
        create_release_tag(
            args.create_release,
            args.default_branch,
        )

    if args.publish_wiki:
        publish_wiki(repo_slug)

    print_next_steps(repo_slug)


if __name__ == "__main__":
    main()
