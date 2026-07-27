#!/usr/bin/env python3
r"""
Fix the VicoVPN post-onboarding NestedScrollView inflation crash.

Root cause:
activity_main.xml sets android:nestedScrollingEnabled on an AndroidX
NestedScrollView. With the AndroidX version in this project, Android applies
that framework attribute while the NestedScrollView constructor is still
running, before NestedScrollingChildHelper is initialized. That causes:
    NullPointerException in NestedScrollView.setNestedScrollingEnabled()

Run from:
    C:\AndroidProjects\VicoVPN

Commands:
    python apply_vicovpn_nestedscroll_runtime_crash_fix.py
    python apply_vicovpn_nestedscroll_runtime_crash_fix.py --install
    python apply_vicovpn_nestedscroll_runtime_crash_fix.py --no-build
    python apply_vicovpn_nestedscroll_runtime_crash_fix.py --rollback

With --install, the script also launches the installed debug app and performs
a short runtime smoke test without clearing app data.

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
import time
import xml.etree.ElementTree as ET


ROOT = Path.cwd()
APP = ROOT / "app"
RES = APP / "src/main/res"
MAIN_LAYOUT = RES / "layout/activity_main.xml"

SOURCE_PATCH = (
    ROOT
    / "apply_vicovpn_notification_center_compact_home.py"
)

BACKUP_ROOT = (
    ROOT
    / ".vicovpn_nestedscroll_crash_fix_backups"
)

STATE_FILE = (
    ROOT
    / ".vicovpn_nestedscroll_crash_fix_state.json"
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

    if not MAIN_LAYOUT.is_file():
        fail(
            "activity_main.xml was not found: "
            + str(MAIN_LAYOUT)
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
            "No nested-scroll crash-fix backup state was found."
        )

    state = json.loads(
        STATE_FILE.read_text(
            encoding="utf-8",
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


def strip_nested_scrolling_attribute_from_tag(tag):
    pattern = re.compile(
        r"""
        \s+
        android:nestedScrollingEnabled
        \s*=\s*
        "(?:true|false)"
        """,
        flags=re.VERBOSE,
    )

    return pattern.subn(
        "",
        tag,
    )


def patch_xml_file(path):
    text = read_text(
        path
    )

    opening_pattern = re.compile(
        r"""
        <androidx\.core\.widget\.NestedScrollView
        \b
        [^>]*>
        """,
        flags=re.VERBOSE | re.DOTALL,
    )

    replacements = 0

    def replace_tag(match):
        nonlocal replacements

        fixed, count = (
            strip_nested_scrolling_attribute_from_tag(
                match.group(0)
            )
        )

        replacements += count

        return fixed

    updated = opening_pattern.sub(
        replace_tag,
        text,
    )

    if replacements:
        write_text(
            path,
            updated,
        )

    return replacements


def patch_all_layout_xml():
    changed_paths = []
    total = 0

    for path in sorted(
        RES.rglob("*.xml")
    ):
        text = read_text(
            path
        )

        if (
            "androidx.core.widget.NestedScrollView"
            not in text
            or
            "android:nestedScrollingEnabled"
            not in text
        ):
            continue

        count = patch_xml_file(
            path
        )

        if count:
            total += count
            changed_paths.append(
                path
            )

    return total, changed_paths


def patch_source_generator():
    if not SOURCE_PATCH.is_file():
        return False

    text = read_text(
        SOURCE_PATCH
    )

    original = text

    patterns = [
        re.compile(
            r"""
            ^[ \t]*
            "android:nestedScrollingEnabled"
            [ \t]*:
            [ \t]*
            "false"
            [ \t]*,
            [ \t]*
            \r?\n
            """,
            flags=re.VERBOSE | re.MULTILINE,
        ),
        re.compile(
            r"""
            ^[ \t]*
            'android:nestedScrollingEnabled'
            [ \t]*:
            [ \t]*
            'false'
            [ \t]*,
            [ \t]*
            \r?\n
            """,
            flags=re.VERBOSE | re.MULTILINE,
        ),
    ]

    for pattern in patterns:
        text = pattern.sub(
            "",
            text,
        )

    if text == original:
        return False

    compile(
        text,
        SOURCE_PATCH.name,
        "exec",
    )

    write_text(
        SOURCE_PATCH,
        text,
    )

    return True


def validate_xml_resources():
    errors = []

    for path in RES.rglob("*.xml"):
        try:
            ET.parse(
                str(path)
            )
        except Exception as error:
            errors.append(
                str(path)
                + ": "
                + str(error)
            )

    if errors:
        fail(
            "XML validation failed:\n"
            + "\n".join(errors)
        )


def validate_crash_attribute_removed():
    offenders = []

    for path in RES.rglob("*.xml"):
        text = read_text(
            path
        )

        opening_pattern = re.compile(
            r"""
            <androidx\.core\.widget\.NestedScrollView
            \b
            [^>]*>
            """,
            flags=re.VERBOSE | re.DOTALL,
        )

        for match in opening_pattern.finditer(
            text
        ):
            if (
                "android:nestedScrollingEnabled"
                in match.group(0)
            ):
                offenders.append(
                    str(
                        path.relative_to(ROOT)
                    )
                )

    if offenders:
        fail(
            "Unsafe android:nestedScrollingEnabled remains on "
            "AndroidX NestedScrollView in:\n"
            + "\n".join(
                sorted(
                    set(offenders)
                )
            )
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


def parse_local_properties():
    path = ROOT / "local.properties"

    if not path.is_file():
        return None

    for raw_line in read_text(
        path
    ).splitlines():
        line = raw_line.strip()

        if not line.startswith(
            "sdk.dir="
        ):
            continue

        value = line.split(
            "=",
            1,
        )[1].strip()

        value = value.replace(
            "\\\\",
            "\\",
        )

        value = re.sub(
            r"\\:",
            ":",
            value,
        )

        return Path(
            value
        )

    return None


def find_adb():
    executable = (
        "adb.exe"
        if os.name == "nt"
        else "adb"
    )

    on_path = shutil.which(
        "adb"
    )

    if on_path:
        return Path(
            on_path
        )

    candidates = []

    for variable in [
        "ANDROID_SDK_ROOT",
        "ANDROID_HOME",
    ]:
        value = os.environ.get(
            variable
        )

        if value:
            candidates.append(
                Path(value)
            )

    local_sdk = parse_local_properties()

    if local_sdk is not None:
        candidates.append(
            local_sdk
        )

    candidates.extend([
        Path(
            r"C:\platform-tools"
        ).parent,
        Path.home()
        / "AppData/Local/Android/Sdk",
        Path.home()
        / "Android/Sdk",
    ])

    direct_candidates = [
        Path(
            r"C:\platform-tools\adb.exe"
        ),
    ]

    for candidate in direct_candidates:
        if candidate.is_file():
            return candidate

    for sdk in candidates:
        candidate = (
            sdk
            / "platform-tools"
            / executable
        )

        if candidate.is_file():
            return candidate

    return None


def run_command(
    command,
    environment=None,
    timeout=None,
):
    return subprocess.run(
        command,
        cwd=str(ROOT),
        env=environment,
        capture_output=False,
        text=True,
        check=False,
        timeout=timeout,
    )


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

    result = run_command(
        command,
        environment=environment,
    )

    if result.returncode != 0:
        print(
            "\nThe runtime crash source was fixed, but Gradle "
            "reported another issue.",
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
            / "app/build/outputs/apk/debug/app-debug.apk",
        )


def adb_capture(
    adb,
    arguments,
):
    return subprocess.run(
        [
            str(adb)
        ]
        + arguments,
        cwd=str(ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )


def detect_debug_package(adb):
    result = adb_capture(
        adb,
        [
            "shell",
            "pm",
            "list",
            "packages",
        ],
    )

    packages = []

    for line in result.stdout.splitlines():
        line = line.strip()

        if line.startswith(
            "package:"
        ):
            packages.append(
                line.split(
                    ":",
                    1,
                )[1]
            )

    for candidate in [
        "com.vicovpn.client.debug",
        "com.vicovpn.client",
    ]:
        if candidate in packages:
            return candidate

    matches = [
        package
        for package in packages
        if "vicovpn" in package.lower()
    ]

    if matches:
        return sorted(
            matches
        )[0]

    return None


def runtime_smoke_test():
    adb = find_adb()

    if adb is None:
        print(
            "\nRuntime smoke test skipped: ADB was not found."
        )
        return

    devices = adb_capture(
        adb,
        [
            "devices",
        ],
    )

    authorized = [
        line.split()[0]
        for line in devices.stdout.splitlines()[1:]
        if (
            len(
                line.split()
            ) >= 2
            and
            line.split()[1] == "device"
        )
    ]

    if not authorized:
        print(
            "\nRuntime smoke test skipped: no authorized "
            "Android device is connected."
        )
        return

    package_name = detect_debug_package(
        adb
    )

    if package_name is None:
        print(
            "\nRuntime smoke test skipped: installed VicoVPN "
            "package was not found."
        )
        return

    print(
        "\nRunning post-install runtime smoke test..."
    )

    adb_capture(
        adb,
        [
            "logcat",
            "-c",
        ],
    )

    adb_capture(
        adb,
        [
            "shell",
            "am",
            "force-stop",
            package_name,
        ],
    )

    launch = adb_capture(
        adb,
        [
            "shell",
            "monkey",
            "-p",
            package_name,
            "-c",
            "android.intent.category.LAUNCHER",
            "1",
        ],
    )

    if launch.returncode != 0:
        fail(
            "ADB could not launch "
            + package_name
            + ":\n"
            + launch.stdout
            + "\n"
            + launch.stderr
        )

    time.sleep(
        5
    )

    pid = adb_capture(
        adb,
        [
            "shell",
            "pidof",
            package_name,
        ],
    )

    logs = adb_capture(
        adb,
        [
            "logcat",
            "-d",
            "-v",
            "brief",
            "AndroidRuntime:E",
            "*:S",
        ],
    )

    fatal = (
        "FATAL EXCEPTION"
        in logs.stdout
        or
        "Unable to start activity"
        in logs.stdout
    )

    if (
        not pid.stdout.strip()
        or fatal
    ):
        report = (
            ROOT
            / "vicovpn_post_fix_smoke_test_failure.txt"
        )

        report.write_text(
            logs.stdout
            + "\n"
            + logs.stderr,
            encoding="utf-8",
        )

        fail(
            "The installed app still exited during the smoke test.\n"
            "Log saved to: "
            + str(report)
        )

    print(
        "RUNTIME SMOKE TEST PASSED"
    )

    print(
        "Process:",
        pid.stdout.strip(),
    )


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Remove the unsafe nestedScrollingEnabled XML "
            "attribute that crashes AndroidX NestedScrollView."
        )
    )

    parser.add_argument(
        "--install",
        action="store_true",
        help=(
            "Build, install, launch, and smoke-test the debug app."
        ),
    )

    parser.add_argument(
        "--no-build",
        action="store_true",
        help=(
            "Apply and validate the source fix without Gradle."
        ),
    )

    parser.add_argument(
        "--rollback",
        action="store_true",
        help=(
            "Restore the files changed by this crash fix."
        ),
    )

    arguments = parser.parse_args()

    ensure_project_root()

    if arguments.rollback:
        rollback()
        return

    candidate_paths = [
        path
        for path in RES.rglob("*.xml")
        if (
            "android:nestedScrollingEnabled"
            in read_text(path)
            and
            "androidx.core.widget.NestedScrollView"
            in read_text(path)
        )
    ]

    if SOURCE_PATCH.is_file():
        candidate_paths.append(
            SOURCE_PATCH
        )

    if not candidate_paths:
        candidate_paths = [
            MAIN_LAYOUT
        ]

    backup = make_backup(
        candidate_paths
    )

    print(
        "Backup:",
        backup,
    )

    removed, changed_paths = (
        patch_all_layout_xml()
    )

    generator_changed = (
        patch_source_generator()
    )

    validate_xml_resources()
    validate_crash_attribute_removed()

    if removed == 0:
        print(
            "The unsafe XML attribute was already absent."
        )
    else:
        print(
            "\nRemoved unsafe NestedScrollView attribute "
            "occurrences:",
            removed,
        )

    if generator_changed:
        print(
            "Also repaired the earlier generator patch so "
            "rerunning it cannot restore the crash."
        )

    print(
        "\nValidated:"
    )

    print(
        "- every AndroidX NestedScrollView inflates without "
        "android:nestedScrollingEnabled"
    )

    print(
        "- all Android XML resources are well-formed"
    )

    print(
        "- no project data or onboarding preferences were cleared"
    )

    if not arguments.no_build:
        run_gradle(
            install=arguments.install
        )

        if arguments.install:
            runtime_smoke_test()
    else:
        print(
            "\nBuild skipped."
        )


if __name__ == "__main__":
    main()
