#!/usr/bin/env python3
r"""
VicoVPN VIP profile polish + home mini-status relocation patch.

Run from:
    C:\AndroidProjects\VicoVPN

Commands:
    python apply_vicovpn_vip_profile_polish_v2.py
    python apply_vicovpn_vip_profile_polish_v2.py --install
    python apply_vicovpn_vip_profile_polish_v2.py --no-build
    python apply_vicovpn_vip_profile_polish_v2.py --rollback

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

VIP_ACTIVITY = JAVA / "profile/VipProfileActivity.kt"
VIP_LAYOUT = RES / "layout/activity_vip_profile.xml"
MAIN_ACTIVITY = JAVA / "MainActivity.kt"
MAIN_LAYOUT = RES / "layout/activity_main.xml"
STRINGS_EN = RES / "values/strings.xml"
STRINGS_FA = RES / "values-fa/strings.xml"

BACKUP_ROOT = ROOT / ".vicovpn_vip_profile_polish_v2_backups"
STATE_FILE = ROOT / ".vicovpn_vip_profile_polish_v2_state.json"

VIP_LAYOUT_XML = '<?xml version="1.0" encoding="utf-8"?>\n<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"\n    xmlns:app="http://schemas.android.com/apk/res-auto"\n    android:id="@+id/vipProfileRoot"\n    android:layout_width="match_parent"\n    android:layout_height="match_parent"\n    android:background="@color/vico_premium_background">\n\n    <androidx.core.widget.NestedScrollView\n        android:layout_width="match_parent"\n        android:layout_height="match_parent"\n        android:clipToPadding="false"\n        android:fillViewport="true"\n        android:overScrollMode="never">\n\n        <LinearLayout\n            android:layout_width="match_parent"\n            android:layout_height="wrap_content"\n            android:orientation="vertical"\n            android:paddingStart="20dp"\n            android:paddingTop="18dp"\n            android:paddingEnd="20dp"\n            android:paddingBottom="34dp">\n\n            <FrameLayout\n                android:layout_width="match_parent"\n                android:layout_height="62dp">\n\n                <TextView\n                    android:id="@+id/vipBackButton"\n                    android:layout_width="48dp"\n                    android:layout_height="48dp"\n                    android:layout_gravity="start|center_vertical"\n                    android:background="?attr/selectableItemBackgroundBorderless"\n                    android:clickable="true"\n                    android:focusable="true"\n                    android:gravity="center"\n                    android:text="‹"\n                    android:textColor="@color/vico_premium_white"\n                    android:textSize="40sp" />\n\n                <LinearLayout\n                    android:layout_width="wrap_content"\n                    android:layout_height="wrap_content"\n                    android:layout_gravity="end|center_vertical"\n                    android:gravity="end"\n                    android:orientation="vertical">\n\n                    <TextView\n                        android:layout_width="wrap_content"\n                        android:layout_height="wrap_content"\n                        android:text="@string/vip_profile_title"\n                        android:textColor="@color/vico_premium_white"\n                        android:textSize="23sp" />\n\n                    <TextView\n                        android:layout_width="wrap_content"\n                        android:layout_height="wrap_content"\n                        android:layout_marginTop="2dp"\n                        android:text="@string/vip_profile_subtitle"\n                        android:textColor="@color/vico_premium_muted"\n                        android:textSize="13sp" />\n\n                </LinearLayout>\n\n            </FrameLayout>\n\n            <com.google.android.material.card.MaterialCardView\n                android:id="@+id/vipKeyCard"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:layout_marginTop="16dp"\n                android:visibility="gone"\n                app:cardBackgroundColor="@color/vico_premium_card"\n                app:cardCornerRadius="28dp"\n                app:cardElevation="0dp"\n                app:strokeColor="@color/vico_premium_outline"\n                app:strokeWidth="1dp">\n\n                <LinearLayout\n                    android:layout_width="match_parent"\n                    android:layout_height="wrap_content"\n                    android:orientation="vertical"\n                    android:padding="20dp">\n\n                    <TextView\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:text="@string/vip_key_title"\n                        android:textColor="@color/vico_premium_white"\n                        android:textSize="19sp" />\n\n                    <TextView\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:layout_marginTop="6dp"\n                        android:text="@string/vip_key_description"\n                        android:textColor="@color/vico_premium_muted"\n                        android:textSize="13sp" />\n\n                    <com.google.android.material.textfield.TextInputLayout\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:layout_marginTop="16dp"\n                        android:hint="@string/vip_key_hint"\n                        app:boxBackgroundMode="outline"\n                        app:boxCornerRadiusBottomEnd="20dp"\n                        app:boxCornerRadiusBottomStart="20dp"\n                        app:boxCornerRadiusTopEnd="20dp"\n                        app:boxCornerRadiusTopStart="20dp"\n                        app:boxStrokeColor="@color/vico_premium_outline">\n\n                        <com.google.android.material.textfield.TextInputEditText\n                            android:id="@+id/vipKeyInput"\n                            android:layout_width="match_parent"\n                            android:layout_height="56dp"\n                            android:maxLines="1"\n                            android:textColor="@color/vico_premium_white"\n                            android:textDirection="ltr"\n                            android:textSize="14sp" />\n\n                    </com.google.android.material.textfield.TextInputLayout>\n\n                    <com.google.android.material.button.MaterialButton\n                        android:id="@+id/vipOpenButton"\n                        android:layout_width="match_parent"\n                        android:layout_height="56dp"\n                        android:layout_marginTop="14dp"\n                        android:text="@string/vip_open_subscription"\n                        android:textAllCaps="false"\n                        android:textColor="@android:color/white"\n                        app:backgroundTint="@color/vico_premium_orange"\n                        app:cornerRadius="26dp" />\n\n                </LinearLayout>\n\n            </com.google.android.material.card.MaterialCardView>\n\n            <com.google.android.material.card.MaterialCardView\n                android:id="@+id/vipLoadingView"\n                android:layout_width="match_parent"\n                android:layout_height="90dp"\n                android:layout_marginTop="16dp"\n                android:visibility="gone"\n                app:cardBackgroundColor="@color/vico_premium_card"\n                app:cardCornerRadius="26dp"\n                app:cardElevation="0dp"\n                app:strokeColor="@color/vico_premium_outline"\n                app:strokeWidth="1dp">\n\n                <LinearLayout\n                    android:layout_width="match_parent"\n                    android:layout_height="match_parent"\n                    android:gravity="center"\n                    android:orientation="horizontal"\n                    android:padding="18dp">\n\n                    <ProgressBar\n                        android:layout_width="30dp"\n                        android:layout_height="30dp"\n                        android:indeterminateTint="@color/vico_premium_orange" />\n\n                    <TextView\n                        android:layout_width="wrap_content"\n                        android:layout_height="wrap_content"\n                        android:layout_marginStart="12dp"\n                        android:text="@string/vip_loading"\n                        android:textColor="@color/vico_premium_white"\n                        android:textSize="14sp" />\n\n                </LinearLayout>\n\n            </com.google.android.material.card.MaterialCardView>\n\n            <com.google.android.material.card.MaterialCardView\n                android:id="@+id/vipErrorCard"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:layout_marginTop="16dp"\n                android:visibility="gone"\n                app:cardBackgroundColor="@color/vico_premium_card"\n                app:cardCornerRadius="26dp"\n                app:cardElevation="0dp"\n                app:strokeColor="@color/vico_vip_danger"\n                app:strokeWidth="1dp">\n\n                <LinearLayout\n                    android:layout_width="match_parent"\n                    android:layout_height="wrap_content"\n                    android:orientation="vertical"\n                    android:padding="18dp">\n\n                    <TextView\n                        android:id="@+id/vipErrorText"\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:textColor="@color/vico_vip_danger"\n                        android:textSize="14sp" />\n\n                    <com.google.android.material.button.MaterialButton\n                        android:id="@+id/vipRetryButton"\n                        android:layout_width="match_parent"\n                        android:layout_height="52dp"\n                        android:layout_marginTop="12dp"\n                        android:text="@string/vip_retry"\n                        android:textAllCaps="false"\n                        app:backgroundTint="@color/vico_premium_card_alt"\n                        app:cornerRadius="24dp"\n                        app:strokeColor="@color/vico_premium_outline"\n                        app:strokeWidth="1dp" />\n\n                </LinearLayout>\n\n            </com.google.android.material.card.MaterialCardView>\n\n            <LinearLayout\n                android:id="@+id/vipDashboardCard"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:layout_marginTop="16dp"\n                android:orientation="vertical"\n                android:visibility="gone">\n\n                <com.google.android.material.card.MaterialCardView\n                    android:id="@+id/vipPlanCard"\n                    android:layout_width="match_parent"\n                    android:layout_height="wrap_content"\n                    app:cardBackgroundColor="@color/vico_premium_card"\n                    app:cardCornerRadius="28dp"\n                    app:cardElevation="0dp"\n                    app:strokeColor="@color/vico_premium_outline"\n                    app:strokeWidth="1dp">\n\n                    <LinearLayout\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:gravity="center_vertical"\n                        android:orientation="horizontal"\n                        android:padding="18dp">\n\n                        <LinearLayout\n                            android:layout_width="0dp"\n                            android:layout_height="wrap_content"\n                            android:layout_weight="1"\n                            android:orientation="vertical">\n\n                            <TextView\n                                android:id="@+id/vipPlanTitle"\n                                android:layout_width="match_parent"\n                                android:layout_height="wrap_content"\n                                android:textColor="@color/vico_premium_white"\n                                android:textSize="24sp" />\n\n                            <TextView\n                                android:id="@+id/vipPlanSubtitle"\n                                android:layout_width="match_parent"\n                                android:layout_height="wrap_content"\n                                android:layout_marginTop="5dp"\n                                android:textColor="@color/vico_premium_muted"\n                                android:textSize="13sp" />\n\n                        </LinearLayout>\n\n                        <com.google.android.material.button.MaterialButton\n                            android:id="@+id/vipRefreshButton"\n                            android:layout_width="112dp"\n                            android:layout_height="48dp"\n                            android:minWidth="0dp"\n                            android:text="@string/vip_refresh"\n                            android:textAllCaps="false"\n                            android:textColor="@color/vico_premium_orange"\n                            app:backgroundTint="@color/vico_premium_card_alt"\n                            app:cornerRadius="22dp"\n                            app:strokeColor="@color/vico_premium_outline"\n                            app:strokeWidth="1dp" />\n\n                    </LinearLayout>\n\n                </com.google.android.material.card.MaterialCardView>\n\n                <LinearLayout\n                    android:layout_width="match_parent"\n                    android:layout_height="76dp"\n                    android:layout_marginTop="10dp"\n                    android:orientation="horizontal"\n                    android:weightSum="2">\n\n                    <com.google.android.material.card.MaterialCardView\n                        android:id="@+id/vipStatusPill"\n                        android:layout_width="0dp"\n                        android:layout_height="match_parent"\n                        android:layout_marginEnd="5dp"\n                        android:layout_weight="1"\n                        app:cardBackgroundColor="@color/vico_premium_card"\n                        app:cardCornerRadius="24dp"\n                        app:cardElevation="0dp"\n                        app:strokeColor="@color/vico_premium_outline"\n                        app:strokeWidth="1dp">\n\n                        <LinearLayout\n                            android:layout_width="match_parent"\n                            android:layout_height="match_parent"\n                            android:gravity="center"\n                            android:orientation="vertical"\n                            android:padding="10dp">\n\n                            <TextView\n                                android:layout_width="wrap_content"\n                                android:layout_height="wrap_content"\n                                android:text="@string/vip_status_label"\n                                android:textColor="@color/vico_premium_muted"\n                                android:textSize="11sp" />\n\n                            <TextView\n                                android:id="@+id/vipStatusText"\n                                android:layout_width="wrap_content"\n                                android:layout_height="wrap_content"\n                                android:layout_marginTop="3dp"\n                                android:maxLines="1"\n                                android:textColor="@color/vico_premium_white"\n                                android:textSize="15sp" />\n\n                        </LinearLayout>\n\n                    </com.google.android.material.card.MaterialCardView>\n\n                    <com.google.android.material.card.MaterialCardView\n                        android:id="@+id/vipExpiryPill"\n                        android:layout_width="0dp"\n                        android:layout_height="match_parent"\n                        android:layout_marginStart="5dp"\n                        android:layout_weight="1"\n                        app:cardBackgroundColor="@color/vico_premium_card"\n                        app:cardCornerRadius="24dp"\n                        app:cardElevation="0dp"\n                        app:strokeColor="@color/vico_premium_outline"\n                        app:strokeWidth="1dp">\n\n                        <LinearLayout\n                            android:layout_width="match_parent"\n                            android:layout_height="match_parent"\n                            android:gravity="center"\n                            android:orientation="vertical"\n                            android:padding="10dp">\n\n                            <TextView\n                                android:layout_width="wrap_content"\n                                android:layout_height="wrap_content"\n                                android:text="@string/vip_expiry_label"\n                                android:textColor="@color/vico_premium_muted"\n                                android:textSize="11sp" />\n\n                            <TextView\n                                android:id="@+id/vipExpiryText"\n                                android:layout_width="wrap_content"\n                                android:layout_height="wrap_content"\n                                android:layout_marginTop="3dp"\n                                android:maxLines="1"\n                                android:textColor="@color/vico_premium_white"\n                                android:textSize="15sp" />\n\n                        </LinearLayout>\n\n                    </com.google.android.material.card.MaterialCardView>\n\n                </LinearLayout>\n\n                <com.google.android.material.card.MaterialCardView\n                    android:id="@+id/vipUsageCard"\n                    android:layout_width="match_parent"\n                    android:layout_height="wrap_content"\n                    android:layout_marginTop="10dp"\n                    app:cardBackgroundColor="@color/vico_premium_card"\n                    app:cardCornerRadius="28dp"\n                    app:cardElevation="0dp"\n                    app:strokeColor="@color/vico_premium_outline"\n                    app:strokeWidth="1dp">\n\n                    <LinearLayout\n                        android:layout_width="match_parent"\n                        android:layout_height="wrap_content"\n                        android:orientation="vertical"\n                        android:padding="18dp">\n\n                        <TextView\n                            android:layout_width="match_parent"\n                            android:layout_height="wrap_content"\n                            android:text="@string/vip_usage_label"\n                            android:textColor="@color/vico_premium_white"\n                            android:textSize="15sp" />\n\n                        <com.google.android.material.progressindicator.LinearProgressIndicator\n                            android:id="@+id/vipUsageProgress"\n                            android:layout_width="match_parent"\n                            android:layout_height="8dp"\n                            android:layout_marginTop="13dp"\n                            app:indicatorColor="@color/vico_premium_orange"\n                            app:trackColor="@color/vico_vip_progress_track"\n                            app:trackCornerRadius="4dp"\n                            app:trackThickness="8dp" />\n\n                        <LinearLayout\n                            android:layout_width="match_parent"\n                            android:layout_height="78dp"\n                            android:layout_marginTop="14dp"\n                            android:orientation="horizontal"\n                            android:weightSum="3">\n\n                            <com.google.android.material.card.MaterialCardView\n                                android:id="@+id/vipUsedPill"\n                                android:layout_width="0dp"\n                                android:layout_height="match_parent"\n                                android:layout_marginEnd="4dp"\n                                android:layout_weight="1"\n                                app:cardBackgroundColor="@color/vico_premium_card_alt"\n                                app:cardCornerRadius="21dp"\n                                app:cardElevation="0dp">\n\n                                <TextView\n                                    android:id="@+id/vipUsedTraffic"\n                                    android:layout_width="match_parent"\n                                    android:layout_height="match_parent"\n                                    android:gravity="center"\n                                    android:maxLines="2"\n                                    android:textColor="@color/vico_premium_muted"\n                                    android:textSize="12sp" />\n\n                            </com.google.android.material.card.MaterialCardView>\n\n                            <com.google.android.material.card.MaterialCardView\n                                android:id="@+id/vipTotalPill"\n                                android:layout_width="0dp"\n                                android:layout_height="match_parent"\n                                android:layout_marginStart="4dp"\n                                android:layout_marginEnd="4dp"\n                                android:layout_weight="1"\n                                app:cardBackgroundColor="@color/vico_premium_card_alt"\n                                app:cardCornerRadius="21dp"\n                                app:cardElevation="0dp">\n\n                                <TextView\n                                    android:id="@+id/vipTotalTraffic"\n                                    android:layout_width="match_parent"\n                                    android:layout_height="match_parent"\n                                    android:gravity="center"\n                                    android:maxLines="2"\n                                    android:textColor="@color/vico_premium_muted"\n                                    android:textSize="12sp" />\n\n                            </com.google.android.material.card.MaterialCardView>\n\n                            <com.google.android.material.card.MaterialCardView\n                                android:id="@+id/vipRemainingPill"\n                                android:layout_width="0dp"\n                                android:layout_height="match_parent"\n                                android:layout_marginStart="4dp"\n                                android:layout_weight="1"\n                                app:cardBackgroundColor="@color/vico_premium_card_alt"\n                                app:cardCornerRadius="21dp"\n                                app:cardElevation="0dp">\n\n                                <TextView\n                                    android:id="@+id/vipRemainingTraffic"\n                                    android:layout_width="match_parent"\n                                    android:layout_height="match_parent"\n                                    android:gravity="center"\n                                    android:maxLines="2"\n                                    android:textColor="@color/vico_premium_muted"\n                                    android:textSize="12sp" />\n\n                            </com.google.android.material.card.MaterialCardView>\n\n                        </LinearLayout>\n\n                    </LinearLayout>\n\n                </com.google.android.material.card.MaterialCardView>\n\n                <com.google.android.material.button.MaterialButton\n                    android:id="@+id/vipSyncButton"\n                    android:layout_width="match_parent"\n                    android:layout_height="58dp"\n                    android:layout_marginTop="10dp"\n                    android:text="@string/vip_sync_routes"\n                    android:textAllCaps="false"\n                    android:textColor="@android:color/white"\n                    app:backgroundTint="@color/vico_premium_orange"\n                    app:cornerRadius="27dp" />\n\n                <com.google.android.material.button.MaterialButton\n                    android:id="@+id/vipRemoveButton"\n                    android:layout_width="match_parent"\n                    android:layout_height="54dp"\n                    android:layout_marginTop="9dp"\n                    android:text="@string/vip_remove_subscription"\n                    android:textAllCaps="false"\n                    android:textColor="@color/vico_vip_danger"\n                    app:backgroundTint="@android:color/transparent"\n                    app:cornerRadius="25dp"\n                    app:strokeColor="@color/vico_vip_danger"\n                    app:strokeWidth="1dp" />\n\n            </LinearLayout>\n\n            <TextView\n                android:id="@+id/vipBannersTitle"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:layout_marginTop="22dp"\n                android:text="@string/vip_announcements"\n                android:textColor="@color/vico_premium_white"\n                android:textSize="18sp"\n                android:visibility="gone" />\n\n            <LinearLayout\n                android:id="@+id/vipBannersContainer"\n                android:layout_width="match_parent"\n                android:layout_height="wrap_content"\n                android:orientation="vertical"\n                android:visibility="gone" />\n\n        </LinearLayout>\n\n    </androidx.core.widget.NestedScrollView>\n\n</FrameLayout>\n'
RENDER_DASHBOARD_KT = '    private fun renderDashboard(\n        response: VipSubscriptionResponse\n    ) {\n        keyCard.visibility =\n            View.GONE\n\n        loadingView.visibility =\n            View.GONE\n\n        errorCard.visibility =\n            View.GONE\n\n        dashboardCard.visibility =\n            View.VISIBLE\n\n        planTitle.text =\n            response.dashboard\n                .title\n                .cleanBannerValue()\n                ?: response.subscription\n                    .plan\n\n        planSubtitle.text =\n            response.dashboard\n                .subtitle\n                .cleanBannerValue()\n                ?: response.subscription\n                    .plan\n\n        statusText.text =\n            response.dashboard\n                .status\n                .cleanBannerValue()\n                ?: "—"\n\n        expiryText.text =\n            response.dashboard\n                .expireText\n                .cleanBannerValue()\n                ?: "—"\n\n        usageProgress.max = 100\n\n        val targetProgress =\n            response.dashboard\n                .progress\n                .coerceIn(\n                    0,\n                    100\n                )\n\n        val indicatorColor =\n            runCatching {\n                Color.parseColor(\n                    response.dashboard\n                        .progressColor\n                )\n            }.getOrDefault(\n                ContextCompat.getColor(\n                    this,\n                    R.color\n                        .vico_premium_orange\n                )\n            )\n\n        usageProgress.setIndicatorColor(\n            indicatorColor\n        )\n\n        val traffic =\n            response.subscription\n                .traffic\n\n        usedTraffic.text =\n            getString(\n                R.string.vip_used_value,\n                traffic.usedGb\n            )\n\n        totalTraffic.text =\n            getString(\n                R.string.vip_total_value,\n                traffic.totalGb\n            )\n\n        remainingTraffic.text =\n            getString(\n                R.string.vip_remaining_value,\n                traffic.remainingGb\n            )\n\n        setButtonsEnabled(\n            true\n        )\n\n        syncButton.isEnabled =\n            response.configs\n                .isNotEmpty() &&\n                !response.subscription\n                    .expiry\n                    .expired\n\n        renderBanners(\n            response.banners\n        )\n\n        AppTypography.apply(\n            this,\n            findViewById<View>(\n                R.id.vipProfileRoot\n            )\n        )\n\n        animateDashboardEntrance(\n            targetProgress\n        )\n    }'
CREATE_BANNER_CARD_KT = '    private fun createBannerCard(\n        banner: VipBanner\n    ): MaterialCardView {\n        val titleText =\n            banner.title\n                .cleanBannerValue()\n                ?: getString(\n                    R.string\n                        .vip_announcement_default_title\n                )\n\n        val messageText =\n            banner.message\n                .cleanBannerValue()\n\n        val actionText =\n            banner.buttonText\n                .cleanBannerValue()\n\n        val actionUrl =\n            banner.buttonUrl\n                .cleanBannerValue()\n\n        val card =\n            MaterialCardView(this).apply {\n                radius =\n                    24.dp.toFloat()\n\n                cardElevation =\n                    0f\n\n                strokeWidth =\n                    1.dp\n\n                strokeColor =\n                    ContextCompat.getColor(\n                        this@VipProfileActivity,\n                        R.color\n                            .vico_premium_outline\n                    )\n\n                setCardBackgroundColor(\n                    ContextCompat.getColor(\n                        this@VipProfileActivity,\n                        R.color\n                            .vico_premium_card\n                    )\n                )\n\n                layoutParams =\n                    LinearLayout.LayoutParams(\n                        LinearLayout\n                            .LayoutParams\n                            .MATCH_PARENT,\n                        LinearLayout\n                            .LayoutParams\n                            .WRAP_CONTENT\n                    ).apply {\n                        topMargin =\n                            10.dp\n                    }\n\n                alpha =\n                    0f\n\n                translationY =\n                    10.dp.toFloat()\n            }\n\n        val content =\n            LinearLayout(this).apply {\n                orientation =\n                    LinearLayout.VERTICAL\n\n                setPadding(\n                    18.dp,\n                    16.dp,\n                    18.dp,\n                    16.dp\n                )\n            }\n\n        val header =\n            LinearLayout(this).apply {\n                orientation =\n                    LinearLayout.HORIZONTAL\n\n                gravity =\n                    Gravity.CENTER_VERTICAL\n            }\n\n        header.addView(\n            TextView(this).apply {\n                text =\n                    titleText\n\n                textSize =\n                    16f\n\n                setTextColor(\n                    ContextCompat.getColor(\n                        this@VipProfileActivity,\n                        R.color\n                            .vico_premium_white\n                    )\n                )\n\n                layoutParams =\n                    LinearLayout.LayoutParams(\n                        0,\n                        LinearLayout\n                            .LayoutParams\n                            .WRAP_CONTENT,\n                        1f\n                    )\n            }\n        )\n\n        if (banner.dismissible) {\n            header.addView(\n                MaterialButton(this).apply {\n                    text = "×"\n                    isAllCaps = false\n                    minWidth = 0\n                    minimumWidth = 0\n                    insetLeft = 0\n                    insetRight = 0\n                    insetTop = 0\n                    insetBottom = 0\n                    cornerRadius = 18.dp\n\n                    backgroundTintList =\n                        ColorStateList.valueOf(\n                            Color.TRANSPARENT\n                        )\n\n                    setTextColor(\n                        ContextCompat.getColor(\n                            this@VipProfileActivity,\n                            R.color\n                                .vico_premium_muted\n                        )\n                    )\n\n                    layoutParams =\n                        LinearLayout.LayoutParams(\n                            40.dp,\n                            40.dp\n                        )\n\n                    setOnClickListener {\n                        dismissBanner(\n                            banner.id\n                        )\n                    }\n                }\n            )\n        }\n\n        content.addView(\n            header\n        )\n\n        if (messageText != null) {\n            content.addView(\n                TextView(this).apply {\n                    text =\n                        messageText\n\n                    textSize =\n                        14f\n\n                    setTextColor(\n                        ContextCompat.getColor(\n                            this@VipProfileActivity,\n                            R.color\n                                .vico_premium_muted\n                        )\n                    )\n\n                    setPadding(\n                        0,\n                        8.dp,\n                        0,\n                        0\n                    )\n                }\n            )\n        }\n\n        if (\n            actionText != null &&\n            actionUrl != null\n        ) {\n            content.addView(\n                MaterialButton(this).apply {\n                    text =\n                        actionText\n\n                    isAllCaps =\n                        false\n\n                    cornerRadius =\n                        20.dp\n\n                    backgroundTintList =\n                        ColorStateList.valueOf(\n                            Color.TRANSPARENT\n                        )\n\n                    strokeWidth =\n                        1.dp\n\n                    strokeColor =\n                        ColorStateList.valueOf(\n                            ContextCompat.getColor(\n                                this@VipProfileActivity,\n                                R.color\n                                    .vico_premium_orange\n                            )\n                        )\n\n                    setTextColor(\n                        ContextCompat.getColor(\n                            this@VipProfileActivity,\n                            R.color\n                                .vico_premium_orange\n                        )\n                    )\n\n                    layoutParams =\n                        LinearLayout.LayoutParams(\n                            LinearLayout\n                                .LayoutParams\n                                .WRAP_CONTENT,\n                            48.dp\n                        ).apply {\n                            topMargin =\n                                12.dp\n                        }\n\n                    setOnClickListener {\n                        openSafeUrl(\n                            actionUrl\n                        )\n                    }\n                }\n            )\n        }\n\n        card.addView(\n            content\n        )\n\n        AppTypography.apply(\n            this,\n            card\n        )\n\n        card.animate()\n            .alpha(1f)\n            .translationY(0f)\n            .setDuration(220L)\n            .setInterpolator(\n                DecelerateInterpolator()\n            )\n            .start()\n\n        return card\n    }'
CONFIRM_REMOVE_KT = '    private fun confirmRemoveSubscription() {\n        val storedKey =\n            preferences()\n                .getString(\n                    KEY_SUBSCRIPTION,\n                    ""\n                )\n                .orEmpty()\n\n        if (\n            activeKey.isBlank() &&\n            storedKey.isBlank()\n        ) {\n            showKeyEntry()\n            return\n        }\n\n        MaterialAlertDialogBuilder(this)\n            .setTitle(\n                R.string.vip_remove_title\n            )\n            .setMessage(\n                R.string.vip_remove_message\n            )\n            .setNegativeButton(\n                android.R.string.cancel,\n                null\n            )\n            .setPositiveButton(\n                R.string.vip_remove_confirm\n            ) {\n                    _,\n                    _ ->\n                removeButton.isEnabled =\n                    false\n\n                val preferencesCleared =\n                    preferences()\n                        .edit()\n                        .remove(\n                            KEY_SUBSCRIPTION\n                        )\n                        .remove(\n                            KEY_DISMISSED\n                        )\n                        .commit()\n\n                val routesCleared =\n                    runCatching {\n                        serverStore\n                            .removeVipServers()\n                    }.isSuccess\n\n                activeKey = ""\n                currentResponse = null\n\n                keyInput.setText("")\n\n                bannersContainer\n                    .removeAllViews()\n\n                bannersTitle.visibility =\n                    View.GONE\n\n                setButtonsEnabled(\n                    true\n                )\n\n                showKeyEntry()\n\n                AppTypography.apply(\n                    this,\n                    findViewById<View>(\n                        R.id.vipProfileRoot\n                    )\n                )\n\n                setResult(\n                    RESULT_OK,\n                    Intent().putExtra(\n                        "vip_subscription_removed",\n                        true\n                    )\n                )\n\n                Toast.makeText(\n                    this,\n                    getString(\n                        if (\n                            preferencesCleared &&\n                            routesCleared\n                        ) {\n                            R.string.vip_removed\n                        } else {\n                            R.string.vip_remove_failed\n                        }\n                    ),\n                    Toast.LENGTH_SHORT\n                ).show()\n            }\n            .show()\n    }'
HELPERS_KT = '    private fun String?.cleanBannerValue():\n        String? {\n        val value =\n            this\n                ?.trim()\n                .orEmpty()\n\n        return value.takeIf {\n            it.isNotBlank() &&\n                !it.equals(\n                    "null",\n                    ignoreCase = true\n                ) &&\n                !it.equals(\n                    "undefined",\n                    ignoreCase = true\n                ) &&\n                it != "—"\n        }\n    }\n\n    private fun animateDashboardEntrance(\n        targetProgress: Int\n    ) {\n        val orderedViews =\n            listOf(\n                R.id.vipPlanCard,\n                R.id.vipStatusPill,\n                R.id.vipExpiryPill,\n                R.id.vipUsageCard,\n                R.id.vipUsedPill,\n                R.id.vipTotalPill,\n                R.id.vipRemainingPill,\n                R.id.vipSyncButton,\n                R.id.vipRemoveButton\n            ).mapNotNull {\n                    id ->\n                findViewById<View>(\n                    id\n                )\n            }\n\n        orderedViews.forEachIndexed {\n                index,\n                view ->\n            view.animate()\n                .cancel()\n\n            view.alpha =\n                0f\n\n            view.translationY =\n                12.dp.toFloat()\n\n            view.scaleX =\n                0.985f\n\n            view.scaleY =\n                0.985f\n\n            view.animate()\n                .alpha(1f)\n                .translationY(0f)\n                .scaleX(1f)\n                .scaleY(1f)\n                .setStartDelay(\n                    index * 34L\n                )\n                .setDuration(\n                    230L\n                )\n                .setInterpolator(\n                    DecelerateInterpolator()\n                )\n                .start()\n        }\n\n        ObjectAnimator.ofInt(\n            usageProgress,\n            "progress",\n            usageProgress.progress,\n            targetProgress\n        ).apply {\n            duration =\n                520L\n\n            interpolator =\n                DecelerateInterpolator()\n\n            start()\n        }\n\n        val statusPill =\n            findViewById<View>(\n                R.id.vipStatusPill\n            )\n\n        statusPill\n            .animate()\n            .scaleX(1.025f)\n            .scaleY(1.025f)\n            .setStartDelay(280L)\n            .setDuration(120L)\n            .withEndAction {\n                statusPill\n                    .animate()\n                    .scaleX(1f)\n                    .scaleY(1f)\n                    .setDuration(160L)\n                    .start()\n            }\n            .start()\n    }\n\n'

EN_STRINGS = {
    "vip_status_label":
        "Status",
    "vip_expiry_label":
        "Expiration",
    "vip_usage_label":
        "Traffic usage",
    "vip_removed":
        "Premium subscription removed",
    "vip_remove_failed":
        "Subscription was removed, but some local routes could not be cleared",
    "vip_announcement_default_title":
        "Announcement",
}

FA_STRINGS = {
    "vip_status_label":
        "وضعیت",
    "vip_expiry_label":
        "زمان باقی‌مانده",
    "vip_usage_label":
        "مصرف اشتراک",
    "vip_removed":
        "اشتراک ویژه حذف شد",
    "vip_remove_failed":
        "اشتراک حذف شد؛ پاک‌سازی بعضی مسیرهای محلی کامل نشد",
    "vip_announcement_default_title":
        "اعلان",
}


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
        VIP_ACTIVITY,
        VIP_LAYOUT,
        MAIN_ACTIVITY,
        MAIN_LAYOUT,
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
            destination = backup / relative
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
            "No VIP profile polish backup state was found."
        )

    state = json.loads(
        STATE_FILE.read_text(
            encoding="utf-8"
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


def add_import(text, import_line):
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


def kotlin_function_bounds(text, signature):
    start = text.find(signature)
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
                return start, index + 1

        index += 1

    fail(
        "Closing brace was not found for "
        + signature
    )


def replace_kotlin_function(
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


def insert_before_function(
    text,
    signature,
    content,
):
    if content.strip() in text:
        return text

    position = text.find(
        signature
    )
    if position < 0:
        fail(
            "Kotlin insertion point was not found: "
            + signature
        )

    return (
        text[:position]
        + content
        + text[position:]
    )


def patch_vip_activity():
    text = read_text(
        VIP_ACTIVITY
    )

    imports = [
        "import android.animation.ObjectAnimator",
        "import android.content.res.ColorStateList",
        "import android.view.animation.DecelerateInterpolator",
        "import com.vicovpn.client.ui.AppTypography",
    ]

    for import_line in imports:
        text = add_import(
            text,
            import_line,
        )

    set_content_pattern = re.compile(
        r"""setContentView\(
\s*R\.layout\.activity_vip_profile
\s*\)""",
        flags=re.MULTILINE,
    )

    match = set_content_pattern.search(
        text
    )
    if match is None:
        fail(
            "VipProfileActivity setContentView point was not found."
        )

    typography_call = """
        AppTypography.apply(
            this,
            findViewById<View>(
                R.id.vipProfileRoot
            )
        )"""

    after = match.end()
    nearby = text[
        after:
        after + 360
    ]

    if "AppTypography.apply" not in nearby:
        text = (
            text[:after]
            + typography_call
            + text[after:]
        )

    on_resume_signature = (
        "    override fun onResume() {"
    )

    if on_resume_signature in text:
        bounds = kotlin_function_bounds(
            text,
            on_resume_signature,
        )
        block = text[
            bounds[0]:
            bounds[1]
        ]

        if (
            "R.id.vipProfileRoot"
            not in block
        ):
            super_marker = (
                "        super.onResume()"
            )
            call = """
        AppTypography.apply(
            this,
            findViewById<View>(
                R.id.vipProfileRoot
            )
        )
"""
            if super_marker in block:
                block = block.replace(
                    super_marker,
                    super_marker + "\n" + call,
                    1,
                )
            else:
                brace = block.find("{")
                block = (
                    block[:brace + 1]
                    + "\n"
                    + super_marker
                    + "\n"
                    + call
                    + block[brace + 1:]
                )

            text = (
                text[:bounds[0]]
                + block
                + text[bounds[1]:]
            )
    else:
        on_resume = """    override fun onResume() {
        super.onResume()

        AppTypography.apply(
            this,
            findViewById<View>(
                R.id.vipProfileRoot
            )
        )
    }

"""
        text = insert_before_function(
            text,
            "    override fun onDestroy() {",
            on_resume,
        )

    text = replace_kotlin_function(
        text,
        "    private fun renderDashboard(",
        RENDER_DASHBOARD_KT,
    )

    text = replace_kotlin_function(
        text,
        "    private fun createBannerCard(",
        CREATE_BANNER_CARD_KT,
    )

    text = replace_kotlin_function(
        text,
        "    private fun confirmRemoveSubscription() {",
        CONFIRM_REMOVE_KT,
    )

    if (
        "private fun String?.cleanBannerValue()"
        not in text
    ):
        text = insert_before_function(
            text,
            "    private fun preferences()",
            HELPERS_KT,
        )

    write_text(
        VIP_ACTIVITY,
        text,
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

    for name, value in mapping.items():
        pattern = (
            r'<string\s+name="'
            + re.escape(name)
            + r'"[^>]*>.*?</string>'
        )
        entry = (
            '    <string name="'
            + name
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
        return start, opening_end

    match = re.match(
        r"<([A-Za-z0-9_.]+)",
        opening,
    )
    if match is None:
        fail(
            "Unable to determine XML tag."
        )

    tag = match.group(1)
    token_pattern = re.compile(
        r"</?"
        + re.escape(tag)
        + r"\b[^>]*>",
        flags=re.DOTALL,
    )

    depth = 0

    for token in token_pattern.finditer(
        text,
        start,
    ):
        value = token.group(0)

        if value.startswith("</"):
            depth -= 1
            if depth == 0:
                return start, token.end()
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


def set_xml_attribute(
    tag,
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

    if pattern.search(tag):
        return pattern.sub(
            replacement,
            tag,
            count=1,
        )

    closing = (
        "/>"
        if tag.rstrip().endswith(
            "/>"
        )
        else ">"
    )

    position = tag.rfind(
        closing
    )

    if position < 0:
        fail(
            "Unable to add XML attribute "
            + name
        )

    return (
        tag[:position].rstrip()
        + "\n"
        + indent
        + replacement
        + "\n"
        + tag[position:]
    )


def restyle_element_opening(
    element,
    attributes,
):
    opening_end = element.find(
        ">"
    ) + 1

    opening = element[
        :opening_end
    ]

    line_end = opening.find(
        "\n"
    )

    if line_end < 0:
        indent = "    "
    else:
        first_attribute_line = opening[
            line_end + 1:
        ]

        indent_match = re.match(
            r"([ \t]+)",
            first_attribute_line,
        )

        indent = (
            indent_match.group(1)
            if indent_match
            else "    "
        )

    for name, value in attributes.items():
        opening = set_xml_attribute(
            opening,
            name,
            value,
            indent,
        )

    return (
        opening
        + element[opening_end:]
    )


def patch_main_layout():
    text = read_text(
        MAIN_LAYOUT
    )

    mini_bounds = element_bounds_by_id(
        text,
        "discoveryMiniCard",
    )

    nav_bounds = element_bounds_by_id(
        text,
        "bottomNavCard",
    )

    if mini_bounds is None:
        print(
            "Notice: discoveryMiniCard was not found; "
            "home pill relocation was skipped."
        )
        return

    if nav_bounds is None:
        fail(
            "bottomNavCard was not found."
        )

    mini = text[
        mini_bounds[0]:
        mini_bounds[1]
    ]

    mini = restyle_element_opening(
        mini,
        {
            "android:layout_width":
                "wrap_content",
            "android:layout_height":
                "56dp",
            "android:layout_gravity":
                "bottom|center_horizontal",
            "android:layout_marginStart":
                "20dp",
            "android:layout_marginEnd":
                "20dp",
            "android:layout_marginBottom":
                "108dp",
            "android:elevation":
                "10dp",
        },
    )

    text_without = (
        text[:mini_bounds[0]]
        + text[mini_bounds[1]:]
    )

    nav_bounds_new = element_bounds_by_id(
        text_without,
        "bottomNavCard",
    )

    insertion = nav_bounds_new[0]

    line_start = text_without.rfind(
        "\n",
        0,
        insertion,
    ) + 1

    text = (
        text_without[:line_start]
        + mini.rstrip()
        + "\n\n"
        + text_without[line_start:]
    )

    write_text(
        MAIN_LAYOUT,
        text,
    )


def patch_main_activity():
    text = read_text(
        MAIN_ACTIVITY
    )

    original = text

    text = text.replace(
        "translationY = -8.dp.toFloat()",
        "translationY = 10.dp.toFloat()",
    )

    if (
        "binding.discoveryMiniCard.updateLayoutParams<FrameLayout.LayoutParams>"
        not in text
    ):
        pattern = re.compile(
            r"""(?P<block>
[ \t]*binding\.bottomNavCard
\s*\.updateLayoutParams<FrameLayout\.LayoutParams>
\s*\{
.*?
^[ \t]*\}
)""",
            flags=re.DOTALL | re.MULTILINE,
        )

        match = pattern.search(
            text
        )

        if match is not None:
            block = match.group(
                "block"
            )

            indent_match = re.match(
                r"([ \t]*)",
                block,
            )

            indent = (
                indent_match.group(1)
                if indent_match
                else "            "
            )

            addition = (
                "\n"
                + indent
                + "binding.discoveryMiniCard\n"
                + indent
                + "    .updateLayoutParams<FrameLayout.LayoutParams> {\n"
                + indent
                + "        bottomMargin =\n"
                + indent
                + "            initialBottomMargin +\n"
                + indent
                + "                bottomInsets.bottom +\n"
                + indent
                + "                90.dp\n"
                + indent
                + "    }"
            )

            text = (
                text[:match.end()]
                + addition
                + text[match.end():]
            )
        else:
            print(
                "Notice: setupWindowInsets bottom-nav block "
                "was not matched; the static safe margin remains active."
            )

    if text != original:
        write_text(
            MAIN_ACTIVITY,
            text,
        )
    else:
        print(
            "MainActivity.kt did not require changes."
        )


def validate_xml():
    for path in [
        VIP_LAYOUT,
        MAIN_LAYOUT,
        STRINGS_EN,
        STRINGS_FA,
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


def validate_required_ids():
    layout = read_text(
        VIP_LAYOUT
    )

    required_ids = [
        "vipProfileRoot",
        "vipBackButton",
        "vipKeyCard",
        "vipLoadingView",
        "vipErrorCard",
        "vipErrorText",
        "vipDashboardCard",
        "vipPlanTitle",
        "vipPlanSubtitle",
        "vipStatusText",
        "vipExpiryText",
        "vipUsageProgress",
        "vipUsedTraffic",
        "vipTotalTraffic",
        "vipRemainingTraffic",
        "vipBannersTitle",
        "vipBannersContainer",
        "vipKeyInput",
        "vipOpenButton",
        "vipRetryButton",
        "vipRefreshButton",
        "vipSyncButton",
        "vipRemoveButton",
        "vipPlanCard",
        "vipStatusPill",
        "vipExpiryPill",
        "vipUsageCard",
        "vipUsedPill",
        "vipTotalPill",
        "vipRemainingPill",
    ]

    missing = [
        view_id
        for view_id in required_ids
        if (
            'android:id="@+id/'
            + view_id
            + '"'
        ) not in layout
    ]

    if missing:
        fail(
            "VIP layout validation failed. Missing IDs: "
            + ", ".join(
                missing
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
        " ".join(
            command
        ),
    )

    result = subprocess.run(
        command,
        cwd=str(ROOT),
        env=environment,
        check=False,
    )

    if result.returncode != 0:
        print(
            "\nThe VIP polish patch was applied, but Gradle "
            "reported another issue.",
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
            "Polish the VIP profile, repair deletion, hide null "
            "banner actions, apply project fonts, and move the "
            "home discovery pill above the bottom navigation."
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
            "Restore the files changed by this patch."
        ),
    )

    arguments = parser.parse_args()

    ensure_project()

    if arguments.rollback:
        rollback()
        return

    changed_files = [
        VIP_ACTIVITY,
        VIP_LAYOUT,
        MAIN_ACTIVITY,
        MAIN_LAYOUT,
        STRINGS_EN,
        STRINGS_FA,
    ]

    backup = make_backup(
        changed_files
    )

    print(
        "Backup:",
        backup,
    )

    write_text(
        VIP_LAYOUT,
        VIP_LAYOUT_XML,
    )

    upsert_strings(
        STRINGS_EN,
        EN_STRINGS,
    )

    upsert_strings(
        STRINGS_FA,
        FA_STRINGS,
    )

    patch_vip_activity()
    patch_main_layout()
    patch_main_activity()
    validate_xml()
    validate_required_ids()

    print(
        "\nApplied:"
    )

    print(
        "- VIP information split into separate rounded cards and pills"
    )

    print(
        "- staggered dashboard and progress animations"
    )

    print(
        "- project typography applied on create, resume, and dynamic banners"
    )

    print(
        "- delete button re-enabled after loading and made persistent"
    )

    print(
        "- saved subscription key and VIP routes removed together"
    )

    print(
        "- null and undefined banner CTA labels and URLs suppressed"
    )

    print(
        "- background discovery pill moved above the bottom navbar"
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
