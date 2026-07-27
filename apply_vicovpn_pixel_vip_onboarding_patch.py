#!/usr/bin/env python3
r"""
VicoVPN pixel-icon, VIP layout, onboarding-choice, and Persian-brand patch.

Run from:
    C:\AndroidProjects\VicoVPN

Commands:
    python apply_vicovpn_pixel_vip_onboarding_patch.py
    python apply_vicovpn_pixel_vip_onboarding_patch.py --install
    python apply_vicovpn_pixel_vip_onboarding_patch.py --no-build
    python apply_vicovpn_pixel_vip_onboarding_patch.py --rollback

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
import xml.etree.ElementTree as ET


ROOT = Path.cwd()
APP = ROOT / "app"
SRC = APP / "src/main"
JAVA = SRC / "java/com/vicovpn/client"
RES = SRC / "res"

MAIN_LAYOUT = RES / "layout/activity_main.xml"
VIP_LAYOUT = RES / "layout/activity_vip_profile.xml"
VIP_ACTIVITY = JAVA / "profile/VipProfileActivity.kt"
ONBOARDING_ACTIVITY = JAVA / "onboarding/OnboardingActivity.kt"
STRINGS_EN = RES / "values/strings.xml"
STRINGS_FA = RES / "values-fa/strings.xml"

PIXEL_UPLOAD_PATH = RES / "drawable/ic_upload_pixel.xml"
PIXEL_DOWNLOAD_PATH = RES / "drawable/ic_download_pixel.xml"
PIXEL_POWER_PATH = RES / "drawable/ic_power_pixel.xml"
PIXEL_CHEVRON_PATH = RES / "drawable/ic_chevron_pixel.xml"

BACKUP_ROOT = ROOT / ".vicovpn_pixel_vip_onboarding_backups"
STATE_FILE = ROOT / ".vicovpn_pixel_vip_onboarding_state.json"

PIXEL_UPLOAD = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M10,13H6V9H10V5H14V9H18V13H14V17H10ZM4,18H20V22H4Z" />\n\n</vector>\n'
PIXEL_DOWNLOAD = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M10,3H14V11H18V15H14V19H10V15H6V11H10ZM4,20H20V23H4Z" />\n\n</vector>\n'
PIXEL_POWER = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="48dp"\n    android:height="48dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M10,2H14V12H10ZM5,5H9V8H7V16H9V19H15V16H17V8H15V5H19V7H21V17H19V20H16V22H8V20H5V17H3V7H5Z" />\n\n</vector>\n'
PIXEL_CHEVRON = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M12,4H16V8H12V12H8V16H12V20H8V16H4V8H8V4Z" />\n\n</vector>\n'
HANDLE_PRIMARY = '    private fun handlePrimary(\n        page: Int\n    ) {\n        if (\n            navigationLocked ||\n            finalLaunchInProgress ||\n            page !in OnboardingSlides\n                .items.indices\n        ) {\n            return\n        }\n\n        when (page) {\n            0 -> {\n                if (\n                    selectedLanguage() ==\n                    null\n                ) {\n                    return\n                }\n\n                if (\n                    getPreferencesStore()\n                        .getBoolean(\n                            KEY_FREE_SERVICE_DECISION,\n                            false\n                        )\n                ) {\n                    applyStoredConnectionPriority()\n                    unlockAndMoveTo(1)\n                } else {\n                    showFreeServicesChoice()\n                }\n            }\n\n            in 1..4 ->\n                unlockAndMoveTo(\n                    page + 1\n                )\n\n            5 ->\n                requestVpnPermission()\n\n            6 ->\n                finishOnboardingSafely()\n        }\n    }'
SELECT_LANGUAGE = '    private fun selectLanguage(\n        languageTag: String\n    ) {\n        if (\n            languageTag != "fa" &&\n            languageTag != "en"\n        ) {\n            return\n        }\n\n        getPreferencesStore()\n            .edit()\n            .putBoolean(\n                KEY_LANGUAGE_SELECTED,\n                true\n            )\n            .putString(\n                KEY_LANGUAGE,\n                languageTag\n            )\n            .apply()\n\n        adapter.refreshLanguage()\n\n        val current =\n            AppCompatDelegate\n                .getApplicationLocales()\n                .toLanguageTags()\n\n        if (current != languageTag) {\n            AppCompatDelegate\n                .setApplicationLocales(\n                    LocaleListCompat\n                        .forLanguageTags(\n                            languageTag\n                        )\n                )\n        }\n    }'
FREE_SERVICE_HELPERS = '    private fun showFreeServicesChoice() {\n        if (\n            navigationLocked ||\n            finalLaunchInProgress\n        ) {\n            return\n        }\n\n        navigationLocked = true\n\n        val dialog =\n            MaterialAlertDialogBuilder(this)\n                .setTitle(\n                    R.string\n                        .onboarding_free_services_title\n                )\n                .setMessage(\n                    R.string\n                        .onboarding_free_services_description\n                )\n                .setPositiveButton(\n                    R.string\n                        .onboarding_free_services_yes\n                ) {\n                        _,\n                        _ ->\n                    saveFreeServicesChoice(\n                        enabled = true\n                    )\n                }\n                .setNegativeButton(\n                    R.string\n                        .onboarding_free_services_no\n                ) {\n                        _,\n                        _ ->\n                    saveFreeServicesChoice(\n                        enabled = false\n                    )\n                }\n                .setCancelable(false)\n                .create()\n\n        dialog.setOnShowListener {\n            runCatching {\n                AppTypography.apply(\n                    this,\n                    dialog.window\n                        ?.decorView\n                        ?: return@setOnShowListener\n                )\n            }\n        }\n\n        dialog.setOnDismissListener {\n            if (\n                !getPreferencesStore()\n                    .getBoolean(\n                        KEY_FREE_SERVICE_DECISION,\n                        false\n                    )\n            ) {\n                navigationLocked = false\n            }\n        }\n\n        dialog.show()\n    }\n\n    private fun saveFreeServicesChoice(\n        enabled: Boolean\n    ) {\n        val committed =\n            getPreferencesStore()\n                .edit()\n                .putBoolean(\n                    KEY_FREE_SERVICE_DECISION,\n                    true\n                )\n                .putBoolean(\n                    KEY_FREE_SERVICE_ENABLED,\n                    enabled\n                )\n                .putBoolean(\n                    KEY_DISCOVERY_STARTED,\n                    enabled\n                )\n                .commit()\n\n        if (!committed) {\n            navigationLocked = false\n\n            Toast.makeText(\n                this,\n                R.string\n                    .onboarding_choice_save_failed,\n                Toast.LENGTH_SHORT\n            ).show()\n\n            return\n        }\n\n        ConnectionPrioritySettings(\n            this\n        ).setMode(\n            if (enabled) {\n                ConnectionPriorityMode\n                    .VIP_AND_FREE\n            } else {\n                ConnectionPriorityMode\n                    .VIP_ONLY\n            }\n        )\n\n        if (enabled) {\n            beginBackgroundPreparation()\n        }\n\n        navigationLocked = false\n        unlockAndMoveTo(1)\n    }\n\n    private fun applyStoredConnectionPriority() {\n        val enabled =\n            getPreferencesStore()\n                .getBoolean(\n                    KEY_FREE_SERVICE_ENABLED,\n                    false\n                )\n\n        ConnectionPrioritySettings(\n            this\n        ).setMode(\n            if (enabled) {\n                ConnectionPriorityMode\n                    .VIP_AND_FREE\n            } else {\n                ConnectionPriorityMode\n                    .VIP_ONLY\n            }\n        )\n\n        if (enabled) {\n            beginBackgroundPreparation()\n        }\n    }\n\n'
PAGE_TRANSFORMER = '        pager.setPageTransformer {\n                page,\n                position ->\n            if (\n                android.animation\n                    .ValueAnimator\n                    .areAnimatorsEnabled()\n            ) {\n                val absolute =\n                    kotlin.math.abs(\n                        position\n                    ).coerceIn(\n                        0f,\n                        1f\n                    )\n\n                val eased =\n                    1f -\n                        (\n                            1f -\n                                absolute\n                            ) *\n                            (\n                                1f -\n                                    absolute\n                                )\n\n                page.alpha =\n                    (\n                        1f -\n                            eased *\n                            0.42f\n                        ).coerceIn(\n                        0.58f,\n                        1f\n                    )\n\n                val scale =\n                    1f -\n                        eased *\n                        0.075f\n\n                page.scaleX =\n                    scale\n\n                page.scaleY =\n                    scale\n\n                page.translationX =\n                    -position *\n                        page.width *\n                        0.065f\n\n                page.translationY =\n                    eased *\n                        12f *\n                        resources\n                            .displayMetrics\n                            .density\n\n                page.rotationY =\n                    position *\n                        -1.35f\n\n                page.cameraDistance =\n                    18_000f *\n                        resources\n                            .displayMetrics\n                            .density\n            } else {\n                page.alpha = 1f\n                page.scaleX = 1f\n                page.scaleY = 1f\n                page.translationX = 0f\n                page.translationY = 0f\n                page.rotationY = 0f\n            }\n        }'
VIP_STATUS_HELPER = '    private fun localizedVipStatus(\n        rawStatus: String,\n        expired: Boolean\n    ): String {\n        val normalized =\n            rawStatus\n                .trim()\n                .lowercase(\n                    java.util.Locale.US\n                )\n\n        val isActive =\n            !expired &&\n                normalized in\n                    setOf(\n                        "active",\n                        "enabled",\n                        "valid",\n                        "online"\n                    )\n\n        val isInactive =\n            expired ||\n                normalized in\n                    setOf(\n                        "inactive",\n                        "disabled",\n                        "expired",\n                        "suspended",\n                        "blocked"\n                    )\n\n        val language =\n            resources\n                .configuration\n                .locales\n                .get(0)\n                .language\n\n        return if (\n            language.equals(\n                "fa",\n                ignoreCase = true\n            )\n        ) {\n            when {\n                isActive ->\n                    getString(\n                        R.string.vip_status_active\n                    )\n\n                isInactive ->\n                    getString(\n                        R.string.vip_status_inactive\n                    )\n\n                else ->\n                    rawStatus.ifBlank {\n                        getString(\n                            R.string.vip_status_inactive\n                        )\n                    }\n            }\n        } else {\n            when {\n                isActive ->\n                    getString(\n                        R.string.vip_status_active\n                    )\n\n                isInactive ->\n                    getString(\n                        R.string.vip_status_inactive\n                    )\n\n                else ->\n                    rawStatus.ifBlank {\n                        getString(\n                            R.string.vip_status_inactive\n                        )\n                    }\n            }\n        }\n    }\n\n'
EN_STRINGS = {'onboarding_free_services_title': 'Use free smart routes?', 'onboarding_free_services_description': 'Free routes can work alongside premium routes. VicoVPN tests them and switches routes automatically when needed.', 'onboarding_free_services_yes': 'Free + premium', 'onboarding_free_services_no': 'Premium only', 'onboarding_choice_save_failed': 'Your selection could not be saved. Please try again.', 'vip_status_active': 'Active', 'vip_status_inactive': 'Inactive'}
FA_STRINGS = {'app_name': 'ویکو وی پی ان', 'onboarding_free_services_title': 'از سرویس\u200cهای رایگان استفاده شود؟', 'onboarding_free_services_description': 'مسیرهای رایگان می\u200cتوانند در کنار مسیرهای ویژه استفاده شوند. ویکو وی پی ان آن\u200cها را خودکار بررسی و در زمان لازم جایگزین می\u200cکند.', 'onboarding_free_services_yes': 'رایگان و ویژه', 'onboarding_free_services_no': 'فقط ویژه', 'onboarding_choice_save_failed': 'انتخاب شما ذخیره نشد. دوباره تلاش کنید.', 'vip_status_active': 'فعال', 'vip_status_inactive': 'غیرفعال'}


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
    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    path.write_text(
        content.rstrip() + "\n",
        encoding="utf-8",
    )

    print(
        "Updated:",
        path.relative_to(ROOT),
    )


def ensure_project():
    required = [
        ROOT / "gradlew.bat",
        MAIN_LAYOUT,
        VIP_LAYOUT,
        VIP_ACTIVITY,
        ONBOARDING_ACTIVITY,
        STRINGS_EN,
        STRINGS_FA,
    ]

    for path in required:
        if not path.exists():
            fail(
                "Run this script from "
                r"C:\AndroidProjects\VicoVPN"
                + ". Missing: "
                + str(path)
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
            "No pixel/VIP/onboarding backup state was found."
        )

    state = json.loads(
        STATE_FILE.read_text(
            encoding="utf-8",
        )
    )

    backup = ROOT / state["backup"]

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


def xml_escape(value):
    return (
        value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
    )


def upsert_strings(path, mapping):
    text = read_text(
        path
    )

    for key, value in mapping.items():
        pattern = (
            r'<string\s+name="'
            + re.escape(key)
            + r'"[^>]*>.*?</string>'
        )

        entry = (
            '    <string name="'
            + key
            + '">'
            + xml_escape(value)
            + '</string>'
        )

        if re.search(
            pattern,
            text,
            flags=re.DOTALL,
        ):
            text = re.sub(
                pattern,
                entry,
                text,
                count=1,
                flags=re.DOTALL,
            )
        else:
            if "</resources>" not in text:
                fail(
                    "Invalid strings resource: "
                    + str(path)
                )

            text = text.replace(
                "</resources>",
                entry
                + "\n</resources>",
                1,
            )

    write_text(
        path,
        text,
    )


def localize_persian_brand():
    text = read_text(
        STRINGS_FA
    )

    pattern = re.compile(
        r"(<string\b[^>]*>)(.*?)(</string>)",
        flags=re.DOTALL,
    )

    brand_pattern = re.compile(
        r"Vico[\s_\-]*VPN",
        flags=re.IGNORECASE,
    )

    def replace_value(match):
        value = brand_pattern.sub(
            "ویکو وی پی ان",
            match.group(2),
        )

        return (
            match.group(1)
            + value
            + match.group(3)
        )

    text = pattern.sub(
        replace_value,
        text,
    )

    write_text(
        STRINGS_FA,
        text,
    )


def element_bounds_from_start(
    text,
    start,
):
    opening_end = text.find(
        ">",
        start,
    )

    if opening_end < 0:
        fail(
            "Malformed XML element."
        )

    opening_end += 1

    opening = text[
        start:
        opening_end
    ]

    if opening.rstrip().endswith(
        "/>"
    ):
        return (
            start,
            opening_end,
        )

    match = re.match(
        r"<([A-Za-z0-9_.]+)",
        opening,
    )

    if match is None:
        fail(
            "Unable to determine XML tag."
        )

    tag = match.group(1)

    tokens = re.compile(
        r"</?"
        + re.escape(tag)
        + r"\b[^>]*>",
        flags=re.DOTALL,
    )

    depth = 0

    for token in tokens.finditer(
        text,
        start,
    ):
        value = token.group(0)

        if value.startswith(
            "</"
        ):
            depth -= 1

            if depth == 0:
                return (
                    start,
                    token.end(),
                )
        elif not value.rstrip().endswith(
            "/>"
        ):
            depth += 1

    fail(
        "Closing XML tag was not found for "
        + tag
    )


def element_bounds_by_id(
    text,
    view_id,
):
    markers = [
        'android:id="@+id/'
        + view_id
        + '"',
        'android:id="@id/'
        + view_id
        + '"',
    ]

    position = -1

    for marker in markers:
        position = text.find(
            marker
        )

        if position >= 0:
            break

    if position < 0:
        return None

    start = text.rfind(
        "<",
        0,
        position,
    )

    if start < 0:
        fail(
            "Malformed XML around "
            + view_id
        )

    return element_bounds_from_start(
        text,
        start,
    )


def enclosing_element(
    text,
    position,
    tag,
):
    search_to = position

    while True:
        start = text.rfind(
            "<" + tag,
            0,
            search_to,
        )

        if start < 0:
            return None

        bounds = element_bounds_from_start(
            text,
            start,
        )

        if bounds[1] > position:
            return bounds

        search_to = start


def set_attribute(
    opening,
    name,
    value,
    indent,
):
    pattern = re.compile(
        re.escape(name)
        + r'="[^"]*"'
    )

    replacement = (
        name
        + '="'
        + value
        + '"'
    )

    if pattern.search(
        opening
    ):
        return pattern.sub(
            replacement,
            opening,
            count=1,
        )

    closing = (
        "/>"
        if opening.rstrip().endswith(
            "/>"
        )
        else ">"
    )

    position = opening.rfind(
        closing
    )

    if position < 0:
        fail(
            "Unable to add XML attribute "
            + name
        )

    return (
        opening[:position].rstrip()
        + "\n"
        + indent
        + replacement
        + "\n"
        + opening[position:]
    )


def remove_attribute(
    opening,
    name,
):
    return re.sub(
        r"\s+"
        + re.escape(name)
        + r'="[^"]*"',
        "",
        opening,
    )


def patch_opening_by_id(
    text,
    view_id,
    attributes,
    remove=(),
):
    bounds = element_bounds_by_id(
        text,
        view_id,
    )

    if bounds is None:
        fail(
            "XML view was not found: "
            + view_id
        )

    opening_end = text.find(
        ">",
        bounds[0],
    ) + 1

    opening = text[
        bounds[0]:
        opening_end
    ]

    line_start = text.rfind(
        "\n",
        0,
        bounds[0],
    ) + 1

    base_indent = text[
        line_start:
        bounds[0]
    ]

    for name in remove:
        opening = remove_attribute(
            opening,
            name,
        )

    for name, value in attributes.items():
        opening = set_attribute(
            opening,
            name,
            value,
            base_indent + "    ",
        )

    return (
        text[:bounds[0]]
        + opening
        + text[opening_end:]
    )


def patch_main_layout():
    text = read_text(
        MAIN_LAYOUT
    )

    replacements = {
        '@drawable/ic_upload"':
            '@drawable/ic_upload_pixel"',
        '@drawable/ic_download"':
            '@drawable/ic_download_pixel"',
        '@drawable/ic_power"':
            '@drawable/ic_power_pixel"',
    }

    for old, new in replacements.items():
        text = text.replace(
            old,
            new,
        )

    priority_bounds = element_bounds_by_id(
        text,
        "connectionPriorityCard",
    )

    if priority_bounds is not None:
        block = text[
            priority_bounds[0]:
            priority_bounds[1]
        ]

        chevron_pattern = re.compile(
            r'<TextView\b[^>]*android:text="›"[^>]*/>',
            flags=re.DOTALL,
        )

        pixel_chevron = """<ImageView
                            android:layout_width="40dp"
                            android:layout_height="40dp"
                            android:contentDescription="@null"
                            android:padding="8dp"
                            android:src="@drawable/ic_chevron_pixel"
                            app:tint="@color/vico_premium_orange" />"""

        block, count = chevron_pattern.subn(
            pixel_chevron,
            block,
            count=1,
        )

        if count:
            text = (
                text[:priority_bounds[0]]
                + block
                + text[priority_bounds[1]:]
            )

    write_text(
        MAIN_LAYOUT,
        text,
    )


def patch_vip_layout():
    text = read_text(
        VIP_LAYOUT
    )

    used_bounds = element_bounds_by_id(
        text,
        "vipUsedPill",
    )

    if used_bounds is None:
        fail(
            "vipUsedPill was not found."
        )

    row_bounds = enclosing_element(
        text,
        used_bounds[0],
        "LinearLayout",
    )

    if row_bounds is None:
        fail(
            "VIP traffic container was not found."
        )

    row_block = text[
        row_bounds[0]:
        row_bounds[1]
    ]

    if (
        "vipTotalPill"
        not in row_block
        or
        "vipRemainingPill"
        not in row_block
    ):
        fail(
            "The detected VIP traffic container is incomplete."
        )

    opening_end = text.find(
        ">",
        row_bounds[0],
    ) + 1

    opening = text[
        row_bounds[0]:
        opening_end
    ]

    line_start = text.rfind(
        "\n",
        0,
        row_bounds[0],
    ) + 1

    base_indent = text[
        line_start:
        row_bounds[0]
    ]

    opening = remove_attribute(
        opening,
        "android:weightSum",
    )

    for name, value in {
        "android:layout_width":
            "match_parent",
        "android:layout_height":
            "wrap_content",
        "android:orientation":
            "vertical",
    }.items():
        opening = set_attribute(
            opening,
            name,
            value,
            base_indent + "    ",
        )

    text = (
        text[:row_bounds[0]]
        + opening
        + text[opening_end:]
    )

    for view_id, top_margin in [
        ("vipUsedPill", "0dp"),
        ("vipTotalPill", "8dp"),
        ("vipRemainingPill", "8dp"),
    ]:
        text = patch_opening_by_id(
            text,
            view_id,
            {
                "android:layout_width":
                    "match_parent",
                "android:layout_height":
                    "66dp",
                "android:layout_marginStart":
                    "0dp",
                "android:layout_marginEnd":
                    "0dp",
                "android:layout_marginTop":
                    top_margin,
            },
            remove=(
                "android:layout_weight",
            ),
        )

    write_text(
        VIP_LAYOUT,
        text,
    )


def add_import(
    text,
    import_line,
):
    if import_line in text:
        return text

    imports = list(
        re.finditer(
            r"^import\s+.+$",
            text,
            flags=re.MULTILINE,
        )
    )

    if not imports:
        fail(
            "Kotlin import block was not found."
        )

    position = imports[-1].end()

    return (
        text[:position]
        + "\n"
        + import_line
        + text[position:]
    )


def kotlin_function_bounds(
    text,
    signature,
):
    start = text.find(
        signature
    )

    if start < 0:
        return None

    brace_start = text.find(
        "{",
        start,
    )

    if brace_start < 0:
        fail(
            "Opening brace was not found for "
            + signature
        )

    depth = 0
    in_string = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    escaped = False
    index = brace_start

    while index < len(text):
        character = text[index]

        next_character = (
            text[index + 1]
            if index + 1 < len(text)
            else ""
        )

        if in_line_comment:
            if character == "\n":
                in_line_comment = False
            index += 1
            continue

        if in_block_comment:
            if (
                character == "*"
                and next_character == "/"
            ):
                in_block_comment = False
                index += 2
                continue
            index += 1
            continue

        if in_string:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                in_string = False
            index += 1
            continue

        if in_char:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == "'":
                in_char = False
            index += 1
            continue

        if (
            character == "/"
            and next_character == "/"
        ):
            in_line_comment = True
            index += 2
            continue

        if (
            character == "/"
            and next_character == "*"
        ):
            in_block_comment = True
            index += 2
            continue

        if character == '"':
            in_string = True
            index += 1
            continue

        if character == "'":
            in_char = True
            index += 1
            continue

        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1

            if depth == 0:
                return (
                    start,
                    index + 1,
                )

        index += 1

    fail(
        "Closing brace was not found for "
        + signature
    )


def replace_function(
    text,
    signature,
    replacement,
):
    bounds = kotlin_function_bounds(
        text,
        signature,
    )

    if bounds is None:
        fail(
            "Kotlin function was not found: "
            + signature
        )

    return (
        text[:bounds[0]]
        + replacement.rstrip()
        + text[bounds[1]:]
    )


def lambda_call_bounds(
    text,
    marker,
):
    start = text.find(
        marker
    )

    if start < 0:
        return None

    brace_start = text.find(
        "{",
        start,
    )

    if brace_start < 0:
        fail(
            "Opening lambda brace was not found for "
            + marker
        )

    depth = 0
    index = brace_start

    while index < len(text):
        character = text[index]

        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1

            if depth == 0:
                return (
                    start,
                    index + 1,
                )

        index += 1

    fail(
        "Closing lambda brace was not found for "
        + marker
    )


def patch_onboarding_activity():
    text = read_text(
        ONBOARDING_ACTIVITY
    )

    for import_line in [
        "import com.google.android.material.dialog.MaterialAlertDialogBuilder",
        "import com.vicovpn.client.server.ConnectionPriorityMode",
        "import com.vicovpn.client.server.ConnectionPrioritySettings",
    ]:
        text = add_import(
            text,
            import_line,
        )

    if (
        "KEY_FREE_SERVICE_DECISION"
        not in text
    ):
        pattern = re.compile(
            r"""(
            private\s+const\s+val\s+
            KEY_DISCOVERY_STARTED
            \s*=\s*
            "onboarding_discovery_started"
            )""",
            flags=re.VERBOSE,
        )

        match = pattern.search(
            text
        )

        if match is None:
            fail(
                "KEY_DISCOVERY_STARTED constant was not found."
            )

        constants = """

        private const val KEY_FREE_SERVICE_DECISION =
            "onboarding_free_service_decision"

        private const val KEY_FREE_SERVICE_ENABLED =
            "onboarding_free_service_enabled"
"""

        text = (
            text[:match.end()]
            + constants
            + text[match.end():]
        )

    text = replace_function(
        text,
        "    private fun handlePrimary(",
        HANDLE_PRIMARY,
    )

    text = replace_function(
        text,
        "    private fun selectLanguage(",
        SELECT_LANGUAGE,
    )

    if (
        "private fun showFreeServicesChoice()"
        not in text
    ):
        position = text.find(
            "    private fun requestVpnPermission()"
        )

        if position < 0:
            fail(
                "requestVpnPermission insertion point was not found."
            )

        text = (
            text[:position]
            + FREE_SERVICE_HELPERS
            + text[position:]
        )

    text = re.sub(
        r"pager\.offscreenPageLimit\s*=\s*\d+",
        "pager.offscreenPageLimit = 3",
        text,
        count=1,
    )

    transformer_bounds = lambda_call_bounds(
        text,
        "        pager.setPageTransformer {",
    )

    if transformer_bounds is None:
        fail(
            "Onboarding page transformer was not found."
        )

    text = (
        text[:transformer_bounds[0]]
        + PAGE_TRANSFORMER.rstrip()
        + text[transformer_bounds[1]:]
    )

    write_text(
        ONBOARDING_ACTIVITY,
        text,
    )


def patch_vip_activity():
    text = read_text(
        VIP_ACTIVITY
    )

    status_pattern = re.compile(
        r"""statusText\.text\s*=\s*
        response\.dashboard\s*
        \.status\s*
        \.cleanBannerValue\(\)\s*
        \?:\s*"—"
        """,
        flags=re.VERBOSE | re.DOTALL,
    )

    replacement = """statusText.text =
            localizedVipStatus(
                rawStatus =
                    response.dashboard
                        .status,
                expired =
                    response.subscription
                        .expiry
                        .expired
            )"""

    text, count = status_pattern.subn(
        replacement,
        text,
        count=1,
    )

    if count == 0:
        fallback = re.compile(
            r"""statusText\.text\s*=\s*
            response\.dashboard\s*
            \.status
            """,
            flags=re.VERBOSE | re.DOTALL,
        )

        text, count = fallback.subn(
            replacement,
            text,
            count=1,
        )

    if count == 0:
        fail(
            "VIP status assignment was not found."
        )

    if (
        "private fun localizedVipStatus("
        not in text
    ):
        marker = (
            "    private fun String?.cleanBannerValue()"
        )

        position = text.find(
            marker
        )

        if position < 0:
            marker = (
                "    private fun preferences()"
            )

            position = text.find(
                marker
            )

        if position < 0:
            fail(
                "VIP helper insertion point was not found."
            )

        text = (
            text[:position]
            + VIP_STATUS_HELPER
            + text[position:]
        )

    write_text(
        VIP_ACTIVITY,
        text,
    )


def validate_xml():
    for path in [
        MAIN_LAYOUT,
        VIP_LAYOUT,
        STRINGS_EN,
        STRINGS_FA,
        PIXEL_UPLOAD_PATH,
        PIXEL_DOWNLOAD_PATH,
        PIXEL_POWER_PATH,
        PIXEL_CHEVRON_PATH,
    ]:
        try:
            ET.parse(
                str(path)
            )
        except Exception as error:
            fail(
                "XML validation failed for "
                + str(path)
                + ": "
                + str(error)
            )


def validate_contract():
    main_layout = read_text(
        MAIN_LAYOUT
    )

    vip_layout = read_text(
        VIP_LAYOUT
    )

    onboarding = read_text(
        ONBOARDING_ACTIVITY
    )

    vip_activity = read_text(
        VIP_ACTIVITY
    )

    for drawable in [
        "ic_upload_pixel",
        "ic_download_pixel",
        "ic_power_pixel",
        "ic_chevron_pixel",
    ]:
        if drawable not in main_layout:
            fail(
                "Main layout is missing "
                + drawable
            )

    for view_id in [
        "vipUsedPill",
        "vipTotalPill",
        "vipRemainingPill",
    ]:
        bounds = element_bounds_by_id(
            vip_layout,
            view_id,
        )

        if bounds is None:
            fail(
                "VIP layout is missing "
                + view_id
            )

        opening = vip_layout[
            bounds[0]:
            vip_layout.find(
                ">",
                bounds[0],
            ) + 1
        ]

        if (
            'android:layout_width="match_parent"'
            not in opening
            or
            'android:layout_weight='
            in opening
        ):
            fail(
                "VIP traffic pill was not converted correctly: "
                + view_id
            )

    for token in [
        "KEY_FREE_SERVICE_DECISION",
        "showFreeServicesChoice()",
        "ConnectionPriorityMode",
        "VIP_AND_FREE",
        "VIP_ONLY",
        "pager.offscreenPageLimit = 3",
        "page.rotationY",
    ]:
        if token not in onboarding:
            fail(
                "Onboarding validation failed. Missing: "
                + token
            )

    if (
        "localizedVipStatus("
        not in vip_activity
    ):
        fail(
            "VIP status localization helper is missing."
        )

    persian = read_text(
        STRINGS_FA
    )

    string_pattern = re.compile(
        r"<string\b[^>]*>(.*?)</string>",
        flags=re.DOTALL,
    )

    for match in string_pattern.finditer(
        persian
    ):
        if re.search(
            r"Vico[\s_\-]*VPN",
            match.group(1),
            flags=re.IGNORECASE,
        ):
            fail(
                "An English VicoVPN brand name remains in "
                "Persian string resources."
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
            "\nThe patch was applied, but Gradle reported "
            "another issue.",
            file=sys.stderr,
        )

        print(
            "Rollback with:\n"
            "  python "
            "apply_vicovpn_pixel_vip_onboarding_patch.py "
            "--rollback",
            file=sys.stderr,
        )

        raise SystemExit(
            result.returncode
        )

    print(
        "\nBUILD SUCCESSFUL"
    )


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Add pixel-style connection icons, stack VIP traffic "
            "pills vertically, localize VIP status, improve "
            "onboarding transitions, ask about free services, "
            "and localize the Persian brand name."
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
            "Restore files changed by this patch."
        ),
    )

    arguments = parser.parse_args()

    ensure_project()

    if arguments.rollback:
        rollback()
        return

    changed_paths = [
        MAIN_LAYOUT,
        VIP_LAYOUT,
        VIP_ACTIVITY,
        ONBOARDING_ACTIVITY,
        STRINGS_EN,
        STRINGS_FA,
        PIXEL_UPLOAD_PATH,
        PIXEL_DOWNLOAD_PATH,
        PIXEL_POWER_PATH,
        PIXEL_CHEVRON_PATH,
    ]

    backup = make_backup(
        changed_paths
    )

    print(
        "Backup:",
        backup,
    )

    write_text(
        PIXEL_UPLOAD_PATH,
        PIXEL_UPLOAD,
    )

    write_text(
        PIXEL_DOWNLOAD_PATH,
        PIXEL_DOWNLOAD,
    )

    write_text(
        PIXEL_POWER_PATH,
        PIXEL_POWER,
    )

    write_text(
        PIXEL_CHEVRON_PATH,
        PIXEL_CHEVRON,
    )

    upsert_strings(
        STRINGS_EN,
        EN_STRINGS,
    )

    upsert_strings(
        STRINGS_FA,
        FA_STRINGS,
    )

    localize_persian_brand()
    patch_main_layout()
    patch_vip_layout()
    patch_vip_activity()
    patch_onboarding_activity()
    validate_xml()
    validate_contract()

    print(
        "\nApplied:"
    )

    print(
        "- pixel-style upload, download, power, and priority icons"
    )

    print(
        "- VIP traffic pills stacked vertically at full width"
    )

    print(
        "- Active/Inactive localized to فعال/غیرفعال in Persian"
    )

    print(
        "- smoother onboarding fade, scale, depth, and movement"
    )

    print(
        "- free-service choice shown after language selection"
    )

    print(
        "- Yes sets connection priority to VIP + free"
    )

    print(
        "- No sets connection priority to VIP only"
    )

    print(
        "- Persian string resources use ویکو وی پی ان instead of VicoVPN"
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
