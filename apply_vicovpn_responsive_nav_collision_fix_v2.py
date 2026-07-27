#!/usr/bin/env python3
r"""
VicoVPN responsive navbar and collision-fix patch v2.

Run from:
    C:\AndroidProjects\VicoVPN

Commands:
    python apply_vicovpn_responsive_nav_collision_fix_v2.py
    python apply_vicovpn_responsive_nav_collision_fix_v2.py --install
    python apply_vicovpn_responsive_nav_collision_fix_v2.py --no-build
    python apply_vicovpn_responsive_nav_collision_fix_v2.py --rollback

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
JAVA = APP / "src/main/java/com/vicovpn/client"
RES = APP / "src/main/res"

MAIN_ACTIVITY = JAVA / "MainActivity.kt"
MAIN_LAYOUT = RES / "layout/activity_main.xml"

SAFE_DRAWABLE_PATH = (
    RES
    / "drawable"
    / "bg_nav_item_selected_safe.xml"
)

BACKUP_ROOT = (
    ROOT
    / ".vicovpn_responsive_nav_v2_backups"
)

STATE_FILE = (
    ROOT
    / ".vicovpn_responsive_nav_v2_state.json"
)

SAFE_DRAWABLE = '<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android">\n    <item android:state_pressed="true">\n        <shape android:shape="rectangle">\n            <corners android:radius="24dp" />\n            <solid android:color="@color/vico_premium_selected_surface" />\n        </shape>\n    </item>\n    <item>\n        <shape android:shape="rectangle">\n            <corners android:radius="24dp" />\n            <solid android:color="@color/vico_premium_selected_surface" />\n        </shape>\n    </item>\n</selector>\n'
SAFE_HEADER = '                <LinearLayout\n                    android:id="@+id/connectionHeaderBar"\n                    android:layout_width="match_parent"\n                    android:layout_height="52dp"\n                    android:baselineAligned="false"\n                    android:gravity="center_vertical"\n                    android:layoutDirection="ltr"\n                    android:orientation="horizontal">\n\n                    <FrameLayout\n                        android:layout_width="56dp"\n                        android:layout_height="52dp">\n\n                        <com.google.android.material.card.MaterialCardView\n                            android:id="@+id/statusIconCard"\n                            android:layout_width="1dp"\n                            android:layout_height="1dp"\n                            android:visibility="gone">\n\n                            <ImageView\n                                android:id="@+id/statusIcon"\n                                android:layout_width="1dp"\n                                android:layout_height="1dp" />\n\n                        </com.google.android.material.card.MaterialCardView>\n\n                        <com.google.android.material.card.MaterialCardView\n                            android:id="@+id/announcementBellCard"\n                            android:layout_width="44dp"\n                            android:layout_height="44dp"\n                            android:layout_gravity="start|center_vertical"\n                            android:clickable="true"\n                            android:focusable="true"\n                            android:foreground="?attr/selectableItemBackgroundBorderless"\n                            android:visibility="gone"\n                            app:cardBackgroundColor="@color/vico_premium_card"\n                            app:cardCornerRadius="22dp"\n                            app:strokeColor="@color/vico_premium_outline"\n                            app:strokeWidth="1dp">\n\n                            <FrameLayout\n                                android:layout_width="match_parent"\n                                android:layout_height="match_parent">\n\n                                <ImageView\n                                    android:layout_width="22dp"\n                                    android:layout_height="22dp"\n                                    android:layout_gravity="center"\n                                    android:contentDescription="@null"\n                                    android:src="@drawable/ic_home_notifications_outline"\n                                    app:tint="@color/vico_premium_white" />\n\n                                <TextView\n                                    android:id="@+id/announcementBadge"\n                                    android:layout_width="19dp"\n                                    android:layout_height="19dp"\n                                    android:layout_gravity="top|end"\n                                    android:background="@drawable/bg_premium_offline_icon"\n                                    android:fontFamily="sans-serif-medium"\n                                    android:gravity="center"\n                                    android:text="1"\n                                    android:textColor="@android:color/white"\n                                    android:textDirection="ltr"\n                                    android:textSize="9sp"\n                                    android:visibility="gone" />\n\n                            </FrameLayout>\n\n                        </com.google.android.material.card.MaterialCardView>\n\n                    </FrameLayout>\n\n                    <TextView\n                        android:id="@+id/connectionTimerText"\n                        android:layout_width="0dp"\n                        android:layout_height="wrap_content"\n                        android:layout_weight="1"\n                        android:ellipsize="end"\n                        android:fontFamily="sans-serif-medium"\n                        android:gravity="center"\n                        android:maxLines="1"\n                        android:text="00:00:00"\n                        android:textColor="@color/vico_premium_orange"\n                        android:textDirection="ltr"\n                        android:textSize="18sp"\n                        android:visibility="gone" />\n\n                    <LinearLayout\n                        android:id="@+id/connectionStatusGroup"\n                        android:layout_width="132dp"\n                        android:layout_height="wrap_content"\n                        android:gravity="end"\n                        android:layoutDirection="locale"\n                        android:orientation="vertical">\n\n                        <TextView\n                            android:layout_width="match_parent"\n                            android:layout_height="wrap_content"\n                            android:ellipsize="end"\n                            android:fontFamily="sans-serif"\n                            android:gravity="end"\n                            android:maxLines="1"\n                            android:text="@string/status_connection"\n                            android:textColor="@color/vico_premium_muted"\n                            android:textDirection="locale"\n                            android:textSize="12sp" />\n\n                        <TextView\n                            android:id="@+id/statusText"\n                            android:layout_width="match_parent"\n                            android:layout_height="wrap_content"\n                            android:layout_marginTop="2dp"\n                            android:ellipsize="end"\n                            android:fontFamily="sans-serif-medium"\n                            android:gravity="end"\n                            android:maxLines="1"\n                            android:text="@string/status_disconnected"\n                            android:textColor="@color/vico_premium_white"\n                            android:textDirection="locale"\n                            android:textSize="17sp" />\n\n                    </LinearLayout>\n\n                </LinearLayout>\n'
RESPONSIVE_NAV = '    <LinearLayout\n        android:id="@+id/bottomNavCard"\n        android:layout_width="match_parent"\n        android:layout_height="72dp"\n        android:layout_gravity="bottom"\n        android:layout_marginStart="18dp"\n        android:layout_marginEnd="18dp"\n        android:layout_marginBottom="14dp"\n        android:baselineAligned="false"\n        android:clipChildren="false"\n        android:clipToPadding="false"\n        android:gravity="center_vertical"\n        android:layoutDirection="locale"\n        android:orientation="horizontal">\n\n        <com.google.android.material.card.MaterialCardView\n            android:id="@+id/profileNavCard"\n            android:layout_width="60dp"\n            android:layout_height="60dp"\n            android:layout_gravity="center_vertical"\n            android:background="@drawable/bg_liquid_glass"\n            android:elevation="0dp"\n            app:cardBackgroundColor="@android:color/transparent"\n            app:cardCornerRadius="30dp"\n            app:cardElevation="0dp"\n            app:cardUseCompatPadding="false"\n            app:strokeColor="@color/vico_premium_nav_outline"\n            app:strokeWidth="1dp">\n\n            <FrameLayout\n                android:id="@+id/navProfileQuick"\n                android:layout_width="match_parent"\n                android:layout_height="match_parent"\n                android:clickable="true"\n                android:focusable="true"\n                android:foreground="?attr/selectableItemBackgroundBorderless">\n\n                <ImageView\n                    android:layout_width="27dp"\n                    android:layout_height="27dp"\n                    android:layout_gravity="center"\n                    android:contentDescription="@string/nav_settings"\n                    android:src="@drawable/ic_nav_profile_outline"\n                    app:tint="@color/vico_premium_nav_icon" />\n\n            </FrameLayout>\n\n        </com.google.android.material.card.MaterialCardView>\n\n        <Space\n            android:layout_width="10dp"\n            android:layout_height="1dp" />\n\n        <com.google.android.material.card.MaterialCardView\n            android:id="@+id/bottomNavPill"\n            android:layout_width="0dp"\n            android:layout_height="60dp"\n            android:layout_gravity="center_vertical"\n            android:layout_weight="1"\n            android:background="@drawable/bg_liquid_glass"\n            android:clipChildren="true"\n            android:clipToPadding="true"\n            android:elevation="0dp"\n            app:cardBackgroundColor="@android:color/transparent"\n            app:cardCornerRadius="30dp"\n            app:cardElevation="0dp"\n            app:cardUseCompatPadding="false"\n            app:strokeColor="@color/vico_premium_nav_outline"\n            app:strokeWidth="1dp">\n\n            <LinearLayout\n                android:layout_width="match_parent"\n                android:layout_height="match_parent"\n                android:baselineAligned="false"\n                android:gravity="center"\n                android:orientation="horizontal"\n                android:paddingStart="7dp"\n                android:paddingEnd="7dp">\n\n                <LinearLayout\n                    android:id="@+id/navSettings"\n                    android:layout_width="0dp"\n                    android:layout_height="46dp"\n                    android:layout_marginStart="2dp"\n                    android:layout_marginEnd="2dp"\n                    android:layout_weight="1"\n                    android:background="@drawable/bg_nav_item_transparent"\n                    android:clickable="true"\n                    android:focusable="true"\n                    android:gravity="center">\n\n                    <ImageView\n                        android:id="@+id/navSettingsIcon"\n                        android:layout_width="26dp"\n                        android:layout_height="26dp"\n                        android:contentDescription="@string/nav_settings"\n                        android:src="@drawable/ic_nav_settings_gear"\n                        app:tint="@color/vico_premium_nav_icon" />\n\n                    <TextView\n                        android:id="@+id/navSettingsText"\n                        android:layout_width="1dp"\n                        android:layout_height="1dp"\n                        android:text="@string/nav_settings"\n                        android:visibility="gone" />\n\n                </LinearLayout>\n\n                <LinearLayout\n                    android:id="@+id/navLogs"\n                    android:layout_width="0dp"\n                    android:layout_height="46dp"\n                    android:layout_marginStart="2dp"\n                    android:layout_marginEnd="2dp"\n                    android:layout_weight="1"\n                    android:background="@drawable/bg_nav_item_transparent"\n                    android:clickable="true"\n                    android:focusable="true"\n                    android:gravity="center">\n\n                    <ImageView\n                        android:id="@+id/navLogsIcon"\n                        android:layout_width="26dp"\n                        android:layout_height="26dp"\n                        android:contentDescription="@string/nav_logs"\n                        android:src="@drawable/ic_nav_monitor"\n                        app:tint="@color/vico_premium_nav_icon" />\n\n                    <TextView\n                        android:id="@+id/navLogsText"\n                        android:layout_width="1dp"\n                        android:layout_height="1dp"\n                        android:text="@string/nav_logs"\n                        android:visibility="gone" />\n\n                </LinearLayout>\n\n                <LinearLayout\n                    android:id="@+id/navServers"\n                    android:layout_width="0dp"\n                    android:layout_height="46dp"\n                    android:layout_marginStart="2dp"\n                    android:layout_marginEnd="2dp"\n                    android:layout_weight="1"\n                    android:background="@drawable/bg_nav_item_transparent"\n                    android:clickable="true"\n                    android:focusable="true"\n                    android:gravity="center">\n\n                    <ImageView\n                        android:id="@+id/navServersIcon"\n                        android:layout_width="26dp"\n                        android:layout_height="26dp"\n                        android:contentDescription="@string/nav_servers"\n                        android:src="@drawable/ic_nav_folder_key"\n                        app:tint="@color/vico_premium_nav_icon" />\n\n                    <TextView\n                        android:id="@+id/navServersText"\n                        android:layout_width="1dp"\n                        android:layout_height="1dp"\n                        android:text="@string/nav_servers"\n                        android:visibility="gone" />\n\n                </LinearLayout>\n\n                <LinearLayout\n                    android:id="@+id/navConnection"\n                    android:layout_width="0dp"\n                    android:layout_height="46dp"\n                    android:layout_marginStart="2dp"\n                    android:layout_marginEnd="2dp"\n                    android:layout_weight="1"\n                    android:background="@drawable/bg_nav_item_selected_safe"\n                    android:clickable="true"\n                    android:focusable="true"\n                    android:gravity="center">\n\n                    <ImageView\n                        android:id="@+id/navConnectionIcon"\n                        android:layout_width="26dp"\n                        android:layout_height="26dp"\n                        android:contentDescription="@string/nav_connection"\n                        android:src="@drawable/ic_nav_home_reference"\n                        app:tint="@color/vico_nav_selected_icon" />\n\n                    <TextView\n                        android:id="@+id/navConnectionText"\n                        android:layout_width="1dp"\n                        android:layout_height="1dp"\n                        android:text="@string/nav_connection"\n                        android:visibility="gone" />\n\n                </LinearLayout>\n\n            </LinearLayout>\n\n        </com.google.android.material.card.MaterialCardView>\n\n    </LinearLayout>\n'
SETUP_INSETS = '    private fun setupWindowInsets() {\n        val initialNavBottomMargin =\n            (\n                binding.bottomNavCard\n                    .layoutParams as\n                    FrameLayout.LayoutParams\n                ).bottomMargin\n\n        fun updateBottomSafeArea(\n            navigationInset: Int\n        ) {\n            val navBottomMargin =\n                initialNavBottomMargin +\n                    navigationInset\n\n            binding.bottomNavCard\n                .updateLayoutParams<\n                    FrameLayout.LayoutParams\n                > {\n                    bottomMargin =\n                        navBottomMargin\n                }\n\n            binding.bottomNavCard.post {\n                if (\n                    isFinishing ||\n                    isDestroyed\n                ) {\n                    return@post\n                }\n\n                val navHeight =\n                    binding.bottomNavCard\n                        .height\n                        .coerceAtLeast(\n                            72.dp\n                        )\n\n                val sectionClearance =\n                    navBottomMargin +\n                        navHeight +\n                        12.dp\n\n                listOf(\n                    binding.connectionSection,\n                    binding.serversSection,\n                    binding.logsSection,\n                    binding.settingsSection\n                ).forEach {\n                        section ->\n                    section.updatePadding(\n                        bottom =\n                            sectionClearance\n                    )\n                }\n\n                binding.discoveryMiniCard\n                    .updateLayoutParams<\n                        FrameLayout.LayoutParams\n                    > {\n                        bottomMargin =\n                            navBottomMargin +\n                                navHeight +\n                                10.dp\n                    }\n            }\n        }\n\n        ViewCompat.setOnApplyWindowInsetsListener(\n            binding.rootContainer\n        ) {\n                _,\n                insets ->\n            val topInsets =\n                insets.getInsets(\n                    WindowInsetsCompat.Type\n                        .statusBars() or\n                        WindowInsetsCompat.Type\n                            .displayCutout()\n                )\n\n            val bottomInsets =\n                insets.getInsets(\n                    WindowInsetsCompat.Type\n                        .navigationBars()\n                )\n\n            binding.screenContainer\n                .updatePadding(\n                    top =\n                        topInsets.top +\n                            20.dp\n                )\n\n            updateBottomSafeArea(\n                bottomInsets.bottom\n            )\n\n            insets\n        }\n\n        binding.bottomNavCard\n            .addOnLayoutChangeListener {\n                    _,\n                    _,\n                    _,\n                    _,\n                    _,\n                    _,\n                    _,\n                    _,\n                    _ ->\n                val rootInsets =\n                    ViewCompat\n                        .getRootWindowInsets(\n                            binding.rootContainer\n                        )\n\n                val bottomInset =\n                    rootInsets\n                        ?.getInsets(\n                            WindowInsetsCompat.Type\n                                .navigationBars()\n                        )\n                        ?.bottom\n                        ?: 0\n\n                updateBottomSafeArea(\n                    bottomInset\n                )\n            }\n\n        ViewCompat.requestApplyInsets(\n            binding.rootContainer\n        )\n    }'
UPDATE_NAV = '    private fun updateNavigationAppearance(\n        selected: MainTab\n    ) {\n        val navItems =\n            listOf(\n                Triple(\n                    MainTab.CONNECTION,\n                    binding.navConnection,\n                    binding.navConnectionIcon\n                ),\n                Triple(\n                    MainTab.SERVERS,\n                    binding.navServers,\n                    binding.navServersIcon\n                ),\n                Triple(\n                    MainTab.LOGS,\n                    binding.navLogs,\n                    binding.navLogsIcon\n                ),\n                Triple(\n                    MainTab.SETTINGS,\n                    binding.navSettings,\n                    binding.navSettingsIcon\n                )\n            )\n\n        navItems.forEach {\n                (tab, view, icon) ->\n            val isSelected =\n                tab == selected\n\n            view.animate()\n                .cancel()\n\n            icon.animate()\n                .cancel()\n\n            view.setBackgroundResource(\n                if (isSelected) {\n                    R.drawable\n                        .bg_nav_item_selected_safe\n                } else {\n                    R.drawable\n                        .bg_nav_item_transparent\n                }\n            )\n\n            icon.imageTintList =\n                ColorStateList.valueOf(\n                    ContextCompat.getColor(\n                        this,\n                        if (isSelected) {\n                            R.color\n                                .vico_nav_selected_icon\n                        } else {\n                            R.color\n                                .vico_premium_nav_icon\n                        }\n                    )\n                )\n\n            view.alpha =\n                if (isSelected) {\n                    1f\n                } else {\n                    0.78f\n                }\n\n            view.animate()\n                .scaleX(\n                    if (isSelected) {\n                        1f\n                    } else {\n                        0.94f\n                    }\n                )\n                .scaleY(\n                    if (isSelected) {\n                        1f\n                    } else {\n                        0.94f\n                    }\n                )\n                .setDuration(\n                    160L\n                )\n                .start()\n\n            icon.animate()\n                .scaleX(\n                    if (isSelected) {\n                        1.04f\n                    } else {\n                        1f\n                    }\n                )\n                .scaleY(\n                    if (isSelected) {\n                        1.04f\n                    } else {\n                        1f\n                    }\n                )\n                .setDuration(\n                    160L\n                )\n                .start()\n        }\n    }'


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
        MAIN_ACTIVITY,
        MAIN_LAYOUT,
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
            "No responsive-navbar backup state was found."
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


def patch_opening_by_id(
    text,
    view_id,
    attributes,
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


def find_smallest_common_xml_ancestor(
    text,
    view_ids,
):
    positions = []

    for view_id in view_ids:
        bounds = element_bounds_by_id(
            text,
            view_id,
        )

        if bounds is None:
            fail(
                "Required connection-header view was not found: "
                + view_id
            )

        positions.append(
            bounds[0]
        )

    earliest = min(
        positions
    )

    latest = max(
        positions
    )

    candidates = []

    opening_pattern = re.compile(
        r"<(?:FrameLayout|LinearLayout)",
    )

    for match in opening_pattern.finditer(
        text,
        0,
        earliest + 1,
    ):
        start = match.start()

        try:
            bounds = element_bounds_from_start(
                text,
                start,
            )
        except SystemExit:
            raise
        except Exception:
            continue

        if (
            bounds[0] <= earliest
            and
            bounds[1] > latest
        ):
            block = text[
                bounds[0]:
                bounds[1]
            ]

            if all(
                (
                    'android:id="@+id/'
                    + view_id
                    + '"'
                ) in block
                or
                (
                    'android:id="@id/'
                    + view_id
                    + '"'
                ) in block
                for view_id in view_ids
            ):
                candidates.append(
                    bounds
                )

    if not candidates:
        return None

    return min(
        candidates,
        key=lambda item:
            item[1] - item[0],
    )


def replace_header(text):
    existing = element_bounds_by_id(
        text,
        "connectionHeaderBar",
    )

    if existing is not None:
        return (
            text[:existing[0]]
            + SAFE_HEADER.rstrip()
            + text[existing[1]:]
        )

    header_bounds = (
        find_smallest_common_xml_ancestor(
            text,
            [
                "statusIconCard",
                "connectionTimerText",
                "statusText",
            ],
        )
    )

    if header_bounds is None:
        fail(
            "Could not locate the shared connection-header "
            "container. The current XML was left unchanged."
        )

    old_header = text[
        header_bounds[0]:
        header_bounds[1]
    ]

    required = [
        "statusIconCard",
        "connectionTimerText",
        "statusText",
    ]

    missing = [
        view_id
        for view_id in required
        if view_id not in old_header
    ]

    if missing:
        fail(
            "The detected connection-header block is incomplete: "
            + ", ".join(
                missing
            )
        )

    return (
        text[:header_bounds[0]]
        + SAFE_HEADER.rstrip()
        + text[header_bounds[1]:]
    )


def replace_bottom_nav(text):
    bounds = element_bounds_by_id(
        text,
        "bottomNavCard",
    )

    if bounds is None:
        fail(
            "bottomNavCard was not found."
        )

    return (
        text[:bounds[0]]
        + RESPONSIVE_NAV.rstrip()
        + text[bounds[1]:]
    )


def patch_layout():
    text = read_text(
        MAIN_LAYOUT
    )

    text = replace_header(
        text
    )

    text = replace_bottom_nav(
        text
    )

    text = patch_opening_by_id(
        text,
        "discoveryMiniCard",
        {
            "android:layout_width":
                "wrap_content",
            "android:layout_height":
                "52dp",
            "android:layout_gravity":
                "bottom|center_horizontal",
            "android:layout_marginStart":
                "24dp",
            "android:layout_marginEnd":
                "24dp",
            "android:layout_marginTop":
                "0dp",
            "android:layout_marginBottom":
                "96dp",
            "android:elevation":
                "0dp",
        },
    )

    text = patch_opening_by_id(
        text,
        "connectionContent",
        {
            "android:paddingBottom":
                "18dp",
        },
    )

    text = text.replace(
        'android:paddingBottom="120dp"',
        'android:paddingBottom="24dp"',
    )

    write_text(
        MAIN_LAYOUT,
        text,
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


def patch_activity():
    text = read_text(
        MAIN_ACTIVITY
    )

    text = replace_function(
        text,
        "    private fun setupWindowInsets() {",
        SETUP_INSETS,
    )

    text = replace_function(
        text,
        "    private fun updateNavigationAppearance(",
        UPDATE_NAV,
    )

    write_text(
        MAIN_ACTIVITY,
        text,
    )


def validate_xml():
    for path in [
        MAIN_LAYOUT,
        SAFE_DRAWABLE_PATH,
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
    layout = read_text(
        MAIN_LAYOUT
    )

    activity = read_text(
        MAIN_ACTIVITY
    )

    for view_id in [
        "connectionHeaderBar",
        "connectionStatusGroup",
        "bottomNavCard",
        "profileNavCard",
        "bottomNavPill",
        "navProfileQuick",
        "navConnection",
        "navServers",
        "navLogs",
        "navSettings",
        "discoveryMiniCard",
    ]:
        if (
            'android:id="@+id/'
            + view_id
            + '"'
        ) not in layout:
            fail(
                "Layout validation failed. Missing ID: "
                + view_id
            )

    for token in [
        "updateBottomSafeArea(",
        "binding.discoveryMiniCard",
        "bg_nav_item_selected_safe",
        "view.animate()",
        ".cancel()",
    ]:
        if token not in activity:
            fail(
                "MainActivity validation failed. Missing: "
                + token
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
            "\nThe responsive navbar patch was applied, "
            "but Gradle reported another issue.",
            file=sys.stderr,
        )

        print(
            "Rollback with:\n"
            "  python "
            "apply_vicovpn_responsive_nav_collision_fix_v2.py "
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
            "Fix VicoVPN navbar collisions, dynamic safe areas, "
            "floating status placement, and header overlap."
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
        MAIN_ACTIVITY,
        MAIN_LAYOUT,
        SAFE_DRAWABLE_PATH,
    ]

    backup = make_backup(
        changed_paths
    )

    print(
        "Backup:",
        backup,
    )

    write_text(
        SAFE_DRAWABLE_PATH,
        SAFE_DRAWABLE,
    )

    # The earlier failed run may already have created this drawable.
    # The layout and Kotlin files are still patched transactionally here.
    patch_layout()
    patch_activity()
    validate_xml()
    validate_contract()

    print(
        "\nApplied:"
    )

    print(
        "- responsive shared bottom navigation"
    )

    print(
        "- fixed gap between profile button and navigation pill"
    )

    print(
        "- selected tab is inset and cannot touch the navbar outline"
    )

    print(
        "- stale tab animations are cancelled before new animations"
    )

    print(
        "- floating discovery status is anchored above measured navbar height"
    )

    print(
        "- every page receives dynamic bottom safe-area padding"
    )

    print(
        "- timer and connection status cannot overlap"
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
