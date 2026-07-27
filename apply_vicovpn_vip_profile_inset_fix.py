#!/usr/bin/env python3
r"""
Fix unsupported MaterialButton inset properties introduced by the VIP profile
polish patch.

Run from:
    C:\AndroidProjects\VicoVPN

Commands:
    python apply_vicovpn_vip_profile_inset_fix.py
    python apply_vicovpn_vip_profile_inset_fix.py --install
    python apply_vicovpn_vip_profile_inset_fix.py --no-build
    python apply_vicovpn_vip_profile_inset_fix.py --rollback

Compatible with Python 3.8+.
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


ROOT = Path.cwd()

VIP_ACTIVITY = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "vicovpn"
    / "client"
    / "profile"
    / "VipProfileActivity.kt"
)

SOURCE_PATCH = (
    ROOT
    / "apply_vicovpn_vip_profile_polish_v2.py"
)

BACKUP_ROOT = (
    ROOT
    / ".vicovpn_vip_profile_inset_fix_backups"
)

STATE_FILE = (
    ROOT
    / ".vicovpn_vip_profile_inset_fix_state.json"
)


def fail(message):
    print(
        "\nERROR: " + str(message),
        file=sys.stderr,
    )
    raise SystemExit(1)


def read_text(path):
    return path.read_text(
        encoding="utf-8-sig",
    )


def write_text(path, content):
    path.write_text(
        content.rstrip() + "\n",
        encoding="utf-8",
    )

    print(
        "Updated:",
        path.relative_to(ROOT),
    )


def ensure_project_root():
    if not (
        ROOT
        / "gradlew.bat"
    ).is_file():
        fail(
            "Run this script from "
            r"C:\AndroidProjects\VicoVPN"
        )

    if not VIP_ACTIVITY.is_file():
        fail(
            "VipProfileActivity.kt was not found: "
            + str(VIP_ACTIVITY)
        )


def make_backup(paths):
    stamp = datetime.datetime.now().strftime(
        "%Y%m%d_%H%M%S"
    )

    backup = BACKUP_ROOT / stamp

    backup.mkdir(
        parents=True,
        exist_ok=False,
    )

    state = {
        "backup":
            str(
                backup.relative_to(ROOT)
            ),
        "files": {},
    }

    for path in paths:
        relative = str(
            path.relative_to(ROOT)
        )

        existed = path.exists()

        state["files"][relative] = {
            "existed": existed,
        }

        if existed:
            destination = (
                backup
                / relative
            )

            destination.parent.mkdir(
                parents=True,
                exist_ok=True,
            )

            shutil.copy2(
                path,
                destination,
            )

    STATE_FILE.write_text(
        json.dumps(
            state,
            indent=2,
        ),
        encoding="utf-8",
    )

    return backup


def rollback():
    if not STATE_FILE.exists():
        fail(
            "No VIP inset-fix backup state was found."
        )

    state = json.loads(
        STATE_FILE.read_text(
            encoding="utf-8"
        )
    )

    backup = (
        ROOT
        / state["backup"]
    )

    for relative, record in state["files"].items():
        target = ROOT / relative
        saved = backup / relative

        if record["existed"]:
            if not saved.exists():
                fail(
                    "Backup file is missing: "
                    + str(saved)
                )

            target.parent.mkdir(
                parents=True,
                exist_ok=True,
            )

            shutil.copy2(
                saved,
                target,
            )
        elif target.exists():
            target.unlink()

    STATE_FILE.unlink()

    print(
        "Rollback complete:",
        backup,
    )


def remove_unsupported_insets(text):
    pattern = re.compile(
        r"""(?m)
        ^[ \t]*
        inset
        (?:Left|Right|Top|Bottom)
        [ \t]*=
        [ \t]*
        [^\r\n]+
        \r?\n
        """,
        flags=re.VERBOSE,
    )

    text, removed = pattern.subn(
        "",
        text,
    )

    return text, removed


def ensure_zero_padding(text):
    marker = """                    minimumWidth = 0
                    cornerRadius = 18.dp
"""

    replacement = """                    minimumWidth = 0
                    setPadding(
                        0,
                        0,
                        0,
                        0
                    )
                    cornerRadius = 18.dp
"""

    if marker in text:
        return text.replace(
            marker,
            replacement,
            1,
        )

    compact_marker = """                    minimumWidth = 0
"""

    if (
        "setPadding(\n                        0,"
        not in text
        and compact_marker in text
    ):
        return text.replace(
            compact_marker,
            compact_marker
            + """                    setPadding(
                        0,
                        0,
                        0,
                        0
                    )
""",
            1,
        )

    return text


def patch_vip_activity():
    text = read_text(
        VIP_ACTIVITY
    )

    text, removed = remove_unsupported_insets(
        text
    )

    text = ensure_zero_padding(
        text
    )

    remaining = re.findall(
        r"\binset(?:Left|Right|Top|Bottom)\b",
        text,
    )

    if remaining:
        fail(
            "Unsupported inset properties remain in "
            "VipProfileActivity.kt: "
            + ", ".join(
                sorted(
                    set(remaining)
                )
            )
        )

    write_text(
        VIP_ACTIVITY,
        text,
    )

    print(
        "Removed unsupported MaterialButton inset assignments:",
        removed,
    )


def patch_original_generator():
    if not SOURCE_PATCH.is_file():
        return

    text = read_text(
        SOURCE_PATCH
    )

    original = text

    text, removed = remove_unsupported_insets(
        text
    )

    text = ensure_zero_padding(
        text
    )

    if text == original:
        print(
            "Original VIP polish generator did not require changes."
        )
        return

    with warnings.catch_warnings():
        warnings.simplefilter(
            "error",
            SyntaxWarning,
        )

        compile(
            text,
            SOURCE_PATCH.name,
            "exec",
        )

    write_text(
        SOURCE_PATCH,
        text,
    )

    print(
        "Also repaired the original VIP polish generator."
    )

    print(
        "Generator inset assignments removed:",
        removed,
    )


def validate_current_source():
    text = read_text(
        VIP_ACTIVITY
    )

    forbidden = [
        "insetLeft",
        "insetRight",
        "insetTop",
        "insetBottom",
    ]

    remaining = [
        token
        for token in forbidden
        if token in text
    ]

    if remaining:
        fail(
            "Validation failed. Unsupported MaterialButton "
            "properties remain: "
            + ", ".join(remaining)
        )

    if (
        "private fun createBannerCard("
        not in text
    ):
        fail(
            "VipProfileActivity.kt no longer contains "
            "createBannerCard()."
        )


def find_java_home():
    candidates = []

    configured = os.environ.get(
        "JAVA_HOME"
    )

    if configured:
        candidates.append(
            Path(configured)
        )

    candidates.extend([
        Path(
            r"C:\Program Files\Android\Android Studio\jbr"
        ),
        Path(
            r"C:\Program Files\Android\Android Studio\jre"
        ),
    ])

    java_on_path = shutil.which(
        "java"
    )

    if java_on_path:
        candidates.append(
            Path(java_on_path)
            .resolve()
            .parent
            .parent
        )

    executable = (
        "java.exe"
        if os.name == "nt"
        else "java"
    )

    for candidate in candidates:
        if (
            candidate
            / "bin"
            / executable
        ).is_file():
            return candidate

    return None


def run_gradle(install):
    java_home = find_java_home()

    if java_home is None:
        fail(
            "Java was not found. Set JAVA_HOME to "
            "Android Studio's jbr folder."
        )

    environment = os.environ.copy()

    environment["JAVA_HOME"] = str(
        java_home
    )

    environment["PATH"] = (
        str(
            java_home
            / "bin"
        )
        + os.pathsep
        + environment.get(
            "PATH",
            "",
        )
    )

    task = (
        "installDebug"
        if install
        else "assembleDebug"
    )

    command = [
        str(
            ROOT
            / "gradlew.bat"
        ),
        "clean",
        task,
        "--no-daemon",
    ]

    print(
        "\nUsing JAVA_HOME:",
        java_home,
    )

    print(
        "Running:",
        " ".join(command),
    )

    result = subprocess.run(
        command,
        cwd=str(ROOT),
        env=environment,
        check=False,
    )

    if result.returncode != 0:
        print(
            "\nThe unsupported inset properties were removed, "
            "but Gradle reported another issue.",
            file=sys.stderr,
        )

        raise SystemExit(
            result.returncode
        )

    print(
        "\nBUILD SUCCESSFUL"
    )

    if not install:
        print(
            "APK:",
            ROOT
            / "app"
            / "build"
            / "outputs"
            / "apk"
            / "debug"
            / "app-debug.apk",
        )


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Remove unsupported MaterialButton inset properties "
            "from VipProfileActivity.kt."
        )
    )

    parser.add_argument(
        "--install",
        action="store_true",
        help=(
            "Build and install on a connected Android device."
        ),
    )

    parser.add_argument(
        "--no-build",
        action="store_true",
        help=(
            "Apply and validate without running Gradle."
        ),
    )

    parser.add_argument(
        "--rollback",
        action="store_true",
        help=(
            "Restore the files changed by this hotfix."
        ),
    )

    arguments = parser.parse_args()

    ensure_project_root()

    if arguments.rollback:
        rollback()
        return

    paths = [
        VIP_ACTIVITY,
    ]

    if SOURCE_PATCH.is_file():
        paths.append(
            SOURCE_PATCH
        )

    backup = make_backup(
        paths
    )

    print(
        "Backup:",
        backup,
    )

    patch_vip_activity()
    patch_original_generator()
    validate_current_source()

    print(
        "\nApplied:"
    )

    print(
        "- removed unsupported insetLeft/insetRight/insetTop/insetBottom"
    )

    print(
        "- preserved the compact dismiss button with zero view padding"
    )

    print(
        "- repaired the original VIP polish generator when present"
    )

    if not arguments.no_build:
        run_gradle(
            install=arguments.install
        )
    else:
        print(
            "\nBuild skipped."
        )


if __name__ == "__main__":
    main()
