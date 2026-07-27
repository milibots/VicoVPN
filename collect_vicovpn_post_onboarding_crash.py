#!/usr/bin/env python3
r"""
Collect the exact VicoVPN post-onboarding crash and current source files.

Run from:
    C:\AndroidProjects\VicoVPN

Normal use:
    python collect_vicovpn_post_onboarding_crash.py

Capture logs that are already in logcat without clearing/relaunching:
    python collect_vicovpn_post_onboarding_crash.py --existing

The script does not modify the Android project or application data.
Compatible with Python 3.8+.
"""

from pathlib import Path
import argparse
import datetime
import os
import re
import shutil
import subprocess
import sys
import zipfile


ROOT = Path.cwd()
OUTPUT_ROOT = ROOT / ".vicovpn_crash_diagnostics"


def fail(message):
    print("\nERROR: " + str(message), file=sys.stderr)
    raise SystemExit(1)


def run(command, check=False, timeout=60):
    result = subprocess.run(
        command,
        cwd=str(ROOT),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
        check=False,
    )

    if check and result.returncode != 0:
        fail(
            "Command failed:\n"
            + " ".join(str(item) for item in command)
            + "\n\n"
            + result.stdout
            + "\n"
            + result.stderr
        )

    return result


def parse_local_properties():
    path = ROOT / "local.properties"

    if not path.is_file():
        return None

    for raw_line in path.read_text(
        encoding="utf-8-sig",
        errors="replace",
    ).splitlines():
        line = raw_line.strip()

        if not line.startswith("sdk.dir="):
            continue

        value = line.split("=", 1)[1].strip()

        value = value.replace(
            "\\\\",
            "\\",
        )

        value = re.sub(
            r"\\:",
            ":",
            value,
        )

        return Path(value)

    return None


def find_adb():
    executable = (
        "adb.exe"
        if os.name == "nt"
        else "adb"
    )

    on_path = shutil.which("adb")

    if on_path:
        return Path(on_path)

    candidates = []

    for variable in [
        "ANDROID_SDK_ROOT",
        "ANDROID_HOME",
    ]:
        value = os.environ.get(variable)

        if value:
            candidates.append(
                Path(value)
            )

    local_sdk = parse_local_properties()

    if local_sdk is not None:
        candidates.append(local_sdk)

    candidates.extend([
        Path.home()
        / "AppData/Local/Android/Sdk",
        Path.home()
        / "Android/Sdk",
    ])

    for sdk in candidates:
        candidate = (
            sdk
            / "platform-tools"
            / executable
        )

        if candidate.is_file():
            return candidate

    return None


def ensure_device(adb):
    result = run([
        str(adb),
        "devices",
    ], check=True)

    devices = []

    for line in result.stdout.splitlines()[1:]:
        parts = line.split()

        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])

    if not devices:
        fail(
            "No authorized Android device was found.\n"
            "Connect the phone, enable USB debugging, and accept "
            "the authorization prompt."
        )

    if len(devices) > 1:
        print(
            "Multiple devices are connected. "
            "ADB will use its default target."
        )


def installed_packages(adb):
    result = run([
        str(adb),
        "shell",
        "pm",
        "list",
        "packages",
    ], check=True)

    packages = []

    for line in result.stdout.splitlines():
        line = line.strip()

        if line.startswith("package:"):
            packages.append(
                line.split(
                    ":",
                    1,
                )[1]
            )

    return packages


def detect_package(adb):
    packages = installed_packages(adb)

    preferred = [
        "com.vicovpn.client.debug",
        "com.vicovpn.client",
    ]

    for package in preferred:
        if package in packages:
            return package

    matches = [
        package
        for package in packages
        if "vicovpn" in package.lower()
    ]

    if len(matches) == 1:
        return matches[0]

    if matches:
        matches.sort(
            key=lambda value: (
                0
                if value.endswith(".debug")
                else 1,
                value,
            )
        )

        return matches[0]

    fail(
        "No installed package containing 'vicovpn' was found."
    )


def copy_if_exists(source, destination_root):
    if not source.is_file():
        return False

    destination = (
        destination_root
        / "project_files"
        / source.relative_to(ROOT)
    )

    destination.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    shutil.copy2(
        source,
        destination,
    )

    return True


def collect_project_files(destination_root):
    candidates = [
        ROOT
        / "app/src/main/java/com/vicovpn/client/MainActivity.kt",
        ROOT
        / "app/src/main/java/com/vicovpn/client/onboarding/OnboardingActivity.kt",
        ROOT
        / "app/src/main/java/com/vicovpn/client/onboarding/OnboardingAdapter.kt",
        ROOT
        / "app/src/main/java/com/vicovpn/client/onboarding/OnboardingDiscoveryCoordinator.kt",
        ROOT
        / "app/src/main/java/com/vicovpn/client/profile/HomeBannerController.kt",
        ROOT
        / "app/src/main/java/com/vicovpn/client/profile/PublicBannerClient.kt",
        ROOT
        / "app/src/main/java/com/vicovpn/client/profile/VipRouteOptimizer.kt",
        ROOT
        / "app/src/main/java/com/vicovpn/client/server/ConnectionPrioritySettings.kt",
        ROOT
        / "app/src/main/res/layout/activity_main.xml",
        ROOT
        / "app/src/main/res/layout/activity_onboarding.xml",
        ROOT
        / "app/src/main/res/layout/item_onboarding_slide.xml",
        ROOT
        / "app/src/main/AndroidManifest.xml",
        ROOT
        / "app/build.gradle.kts",
        ROOT
        / "app/build.gradle",
        ROOT
        / "build.gradle.kts",
        ROOT
        / "build.gradle",
        ROOT
        / "gradle.properties",
        ROOT
        / "settings.gradle.kts",
        ROOT
        / "settings.gradle",
    ]

    copied = 0

    for source in candidates:
        if copy_if_exists(
            source,
            destination_root,
        ):
            copied += 1

    return copied


def write_command_result(path, result):
    path.write_text(
        "RETURN CODE: "
        + str(result.returncode)
        + "\n\nSTDOUT\n"
        + result.stdout
        + "\n\nSTDERR\n"
        + result.stderr,
        encoding="utf-8",
    )


def extract_crash_context(log_text, package_name):
    lines = log_text.splitlines()

    markers = [
        "FATAL EXCEPTION",
        "AndroidRuntime",
        "Unable to start activity",
        "Unable to resume activity",
        "Unable to destroy activity",
        "Process: " + package_name,
        "Caused by:",
        "RuntimeException",
        "InflateException",
        "UninitializedPropertyAccessException",
        "NullPointerException",
        "ClassCastException",
        "Resources$NotFoundException",
        "NoSuchMethodError",
        "VerifyError",
    ]

    interesting = set()

    for index, line in enumerate(lines):
        if any(marker in line for marker in markers):
            start = max(
                0,
                index - 25,
            )
            end = min(
                len(lines),
                index + 140,
            )

            interesting.update(
                range(
                    start,
                    end,
                )
            )

    package_indexes = [
        index
        for index, line in enumerate(lines)
        if package_name in line
    ]

    for index in package_indexes[-120:]:
        start = max(
            0,
            index - 4,
        )
        end = min(
            len(lines),
            index + 10,
        )

        interesting.update(
            range(
                start,
                end,
            )
        )

    if not interesting:
        return (
            "No FATAL EXCEPTION marker was found in the captured "
            "logcat buffer.\n\n"
            "Re-run the collector, reproduce the crash, and press "
            "Enter immediately after the app closes.\n"
        )

    output = []
    previous = -2

    for index in sorted(interesting):
        if index > previous + 1:
            output.append(
                "\n"
                + "=" * 88
                + "\n"
            )

        output.append(
            lines[index]
        )

        previous = index

    return "\n".join(output) + "\n"


def create_zip(source_directory, zip_path):
    with zipfile.ZipFile(
        zip_path,
        "w",
        compression=zipfile.ZIP_DEFLATED,
    ) as archive:
        for path in source_directory.rglob("*"):
            if path.is_file():
                archive.write(
                    path,
                    path.relative_to(
                        source_directory
                    ),
                )


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Collect the exact post-onboarding Android crash and "
            "the current VicoVPN source files."
        )
    )

    parser.add_argument(
        "--existing",
        action="store_true",
        help=(
            "Do not clear or relaunch the app; collect the current "
            "logcat buffer."
        ),
    )

    arguments = parser.parse_args()

    if not (
        ROOT
        / "gradlew.bat"
    ).is_file():
        fail(
            "Run this script from "
            r"C:\AndroidProjects\VicoVPN"
        )

    adb = find_adb()

    if adb is None:
        fail(
            "ADB was not found. Ensure Android SDK platform-tools "
            "is installed or sdk.dir exists in local.properties."
        )

    ensure_device(adb)

    package_name = detect_package(adb)

    stamp = datetime.datetime.now().strftime(
        "%Y%m%d_%H%M%S"
    )

    output_directory = (
        OUTPUT_ROOT
        / stamp
    )

    output_directory.mkdir(
        parents=True,
        exist_ok=False,
    )

    print(
        "ADB:",
        adb,
    )

    print(
        "Package:",
        package_name,
    )

    copied = collect_project_files(
        output_directory
    )

    print(
        "Collected project files:",
        copied,
    )

    device_info = run([
        str(adb),
        "shell",
        "getprop",
    ])

    write_command_result(
        output_directory
        / "device_properties.txt",
        device_info,
    )

    package_info = run([
        str(adb),
        "shell",
        "dumpsys",
        "package",
        package_name,
    ])

    write_command_result(
        output_directory
        / "package_info.txt",
        package_info,
    )

    if not arguments.existing:
        run([
            str(adb),
            "logcat",
            "-c",
        ])

        run([
            str(adb),
            "shell",
            "am",
            "force-stop",
            package_name,
        ])

        launch = run([
            str(adb),
            "shell",
            "monkey",
            "-p",
            package_name,
            "-c",
            "android.intent.category.LAUNCHER",
            "1",
        ])

        write_command_result(
            output_directory
            / "launch_result.txt",
            launch,
        )

        print()
        print(
            "The app was launched."
        )
        print(
            "Complete onboarding until the app closes or crashes."
        )
        input(
            "Immediately after the crash, press Enter here..."
        )

    logcat = run([
        str(adb),
        "logcat",
        "-d",
        "-v",
        "threadtime",
    ], timeout=120)

    full_log = (
        logcat.stdout
        + "\n"
        + logcat.stderr
    )

    (
        output_directory
        / "logcat_full.txt"
    ).write_text(
        full_log,
        encoding="utf-8",
    )

    filtered = extract_crash_context(
        full_log,
        package_name,
    )

    (
        output_directory
        / "crash_filtered.txt"
    ).write_text(
        filtered,
        encoding="utf-8",
    )

    exit_info = run([
        str(adb),
        "shell",
        "dumpsys",
        "activity",
        "exit-info",
        package_name,
    ])

    write_command_result(
        output_directory
        / "activity_exit_info.txt",
        exit_info,
    )

    process_info = run([
        str(adb),
        "shell",
        "pidof",
        package_name,
    ])

    write_command_result(
        output_directory
        / "process_state.txt",
        process_info,
    )

    summary = (
        "VicoVPN post-onboarding crash diagnostics\n"
        + "Timestamp: "
        + stamp
        + "\nPackage: "
        + package_name
        + "\nADB: "
        + str(adb)
        + "\nProject files copied: "
        + str(copied)
        + "\n\n"
        + "Open crash_filtered.txt first.\n"
    )

    (
        output_directory
        / "README.txt"
    ).write_text(
        summary,
        encoding="utf-8",
    )

    zip_path = (
        ROOT
        / (
            "vicovpn_post_onboarding_crash_"
            + stamp
            + ".zip"
        )
    )

    create_zip(
        output_directory,
        zip_path,
    )

    print()
    print(
        "Crash report:",
        output_directory
        / "crash_filtered.txt",
    )

    print(
        "Complete bundle:",
        zip_path,
    )

    print()
    print(
        "Upload the generated ZIP file in this chat."
    )


if __name__ == "__main__":
    main()
