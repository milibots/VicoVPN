#!/usr/bin/env python3
r"""
VicoVPN pixel-home-icons + free-service onboarding slide patch.

Run from:
    C:\AndroidProjects\VicoVPN

Commands:
    python apply_vicovpn_main_icons_free_service_slide.py
    python apply_vicovpn_main_icons_free_service_slide.py --install
    python apply_vicovpn_main_icons_free_service_slide.py --no-build
    python apply_vicovpn_main_icons_free_service_slide.py --rollback
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
ONBOARDING_DIR = JAVA / "onboarding"
ONBOARDING_MODEL = ONBOARDING_DIR / "OnboardingSlide.kt"
ONBOARDING_ADAPTER = ONBOARDING_DIR / "OnboardingAdapter.kt"
ONBOARDING_ACTIVITY = ONBOARDING_DIR / "OnboardingActivity.kt"
MAIN_ACTIVITY = JAVA / "MainActivity.kt"
MAIN_LAYOUT = RES / "layout/activity_main.xml"
VIP_LAYOUT = RES / "layout/activity_vip_profile.xml"
STRINGS_EN = RES / "values/strings.xml"
STRINGS_FA = RES / "values-fa/strings.xml"
DRAWABLE_UPLOAD = RES / "drawable/ic_upload_pixel.xml"
DRAWABLE_DOWNLOAD = RES / "drawable/ic_download_pixel.xml"
DRAWABLE_POWER = RES / "drawable/ic_power_pixel.xml"
DRAWABLE_CHEVRON = RES / "drawable/ic_chevron_pixel.xml"
BACKUP_ROOT = ROOT / ".vicovpn_main_icons_free_service_slide_backups"
STATE_FILE = ROOT / ".vicovpn_main_icons_free_service_slide_state.json"
NS_ANDROID = "http://schemas.android.com/apk/res/android"
NS_APP = "http://schemas.android.com/apk/res-auto"

MODEL_KT = 'package com.vicovpn.client.onboarding\n\nimport androidx.annotation.DrawableRes\nimport androidx.annotation.StringRes\nimport com.vicovpn.client.R\n\ndata class OnboardingSlide(\n    val id: String,\n    @DrawableRes val mascotRes: Int,\n    @StringRes val titleRes: Int,\n    @StringRes val descriptionRes: Int,\n    @StringRes val primaryActionRes: Int,\n    @StringRes val mascotDescriptionRes: Int,\n    val skipAllowed: Boolean,\n    val requiresAction: Boolean\n)\n\nobject OnboardingSlides {\n    val items =\n        listOf(\n            OnboardingSlide(\n                id = "welcome",\n                mascotRes = R.drawable.onboarding_01_welcome,\n                titleRes = R.string.ob7_welcome_title,\n                descriptionRes = R.string.ob7_welcome_description,\n                primaryActionRes = R.string.ob7_continue,\n                mascotDescriptionRes = R.string.ob7_welcome_mascot_description,\n                skipAllowed = false,\n                requiresAction = true\n            ),\n            OnboardingSlide(\n                id = "free_services",\n                mascotRes = R.drawable.onboarding_03_smart_server_scan,\n                titleRes = R.string.ob7_free_service_title,\n                descriptionRes = R.string.ob7_free_service_description,\n                primaryActionRes = R.string.ob7_continue,\n                mascotDescriptionRes = R.string.ob7_smart_mascot_description,\n                skipAllowed = false,\n                requiresAction = true\n            ),\n            OnboardingSlide(\n                id = "privacy",\n                mascotRes = R.drawable.onboarding_02_privacy_protection,\n                titleRes = R.string.ob7_privacy_title,\n                descriptionRes = R.string.ob7_privacy_description,\n                primaryActionRes = R.string.ob7_continue,\n                mascotDescriptionRes = R.string.ob7_privacy_mascot_description,\n                skipAllowed = true,\n                requiresAction = false\n            ),\n            OnboardingSlide(\n                id = "smart_scan",\n                mascotRes = R.drawable.onboarding_03_smart_server_scan,\n                titleRes = R.string.ob7_smart_title,\n                descriptionRes = R.string.ob7_smart_description,\n                primaryActionRes = R.string.ob7_continue,\n                mascotDescriptionRes = R.string.ob7_smart_mascot_description,\n                skipAllowed = true,\n                requiresAction = false\n            ),\n            OnboardingSlide(\n                id = "speed",\n                mascotRes = R.drawable.onboarding_04_speed_performance,\n                titleRes = R.string.ob7_speed_title,\n                descriptionRes = R.string.ob7_speed_description,\n                primaryActionRes = R.string.ob7_continue,\n                mascotDescriptionRes = R.string.ob7_speed_mascot_description,\n                skipAllowed = true,\n                requiresAction = false\n            ),\n            OnboardingSlide(\n                id = "split_tunneling",\n                mascotRes = R.drawable.onboarding_05_split_tunneling,\n                titleRes = R.string.ob7_split_title,\n                descriptionRes = R.string.ob7_split_description,\n                primaryActionRes = R.string.ob7_continue,\n                mascotDescriptionRes = R.string.ob7_split_mascot_description,\n                skipAllowed = true,\n                requiresAction = false\n            ),\n            OnboardingSlide(\n                id = "permission",\n                mascotRes = R.drawable.onboarding_06_connection_setup,\n                titleRes = R.string.ob7_setup_title,\n                descriptionRes = R.string.ob7_setup_description,\n                primaryActionRes = R.string.ob7_allow_vpn_connection,\n                mascotDescriptionRes = R.string.ob7_setup_mascot_description,\n                skipAllowed = false,\n                requiresAction = true\n            ),\n            OnboardingSlide(\n                id = "ready",\n                mascotRes = R.drawable.onboarding_07_ready_connected,\n                titleRes = R.string.ob7_ready_title,\n                descriptionRes = R.string.ob7_ready_description,\n                primaryActionRes = R.string.ob7_start_using,\n                mascotDescriptionRes = R.string.ob7_ready_mascot_description,\n                skipAllowed = false,\n                requiresAction = true\n            )\n        )\n}\n'
ADAPTER_KT = 'package com.vicovpn.client.onboarding\n\nimport android.animation.ValueAnimator\nimport android.content.res.ColorStateList\nimport android.view.LayoutInflater\nimport android.view.View\nimport android.view.ViewGroup\nimport android.widget.ImageButton\nimport android.widget.ImageView\nimport android.widget.LinearLayout\nimport android.widget.TextView\nimport androidx.core.content.ContextCompat\nimport androidx.core.view.ViewCompat\nimport androidx.recyclerview.widget.RecyclerView\nimport com.google.android.material.button.MaterialButton\nimport com.google.android.material.progressindicator.LinearProgressIndicator\nimport com.vicovpn.client.R\nimport com.vicovpn.client.ui.AppTypography\nimport kotlin.math.roundToInt\n\nclass OnboardingAdapter(\n    private val slides: List<OnboardingSlide>,\n    private val selectedLanguage: () -> String?,\n    private val selectedFreeMode: () -> Boolean?,\n    private val onLanguageSelected: (String) -> Unit,\n    private val onFreeModeSelected: (Boolean) -> Unit,\n    private val onPrimary: (Int) -> Unit,\n    private val onBack: (Int) -> Unit,\n    private val onSkip: (Int) -> Unit\n) : RecyclerView.Adapter<\n    OnboardingAdapter.SlideHolder\n    >() {\n\n    private var permissionDenied = false\n    private var actionPending = false\n    private var discoveryState =\n        OnboardingDiscoveryCoordinator.State.IDLE\n\n    override fun onCreateViewHolder(\n        parent: ViewGroup,\n        viewType: Int\n    ): SlideHolder {\n        return SlideHolder(\n            LayoutInflater.from(\n                parent.context\n            ).inflate(\n                R.layout.item_onboarding_slide,\n                parent,\n                false\n            )\n        )\n    }\n\n    override fun getItemCount(): Int =\n        slides.size\n\n    override fun onBindViewHolder(\n        holder: SlideHolder,\n        position: Int\n    ) {\n        holder.bind(\n            slide = slides[position],\n            position = position\n        )\n    }\n\n    fun setPermissionDenied(\n        denied: Boolean\n    ) {\n        permissionDenied = denied\n        notifyItemChanged(6)\n    }\n\n    fun setActionPending(\n        pending: Boolean\n    ) {\n        actionPending = pending\n        notifyItemChanged(6)\n    }\n\n    fun setDiscoveryState(\n        state: OnboardingDiscoveryCoordinator.State\n    ) {\n        discoveryState = state\n        notifyItemRangeChanged(\n            2,\n            itemCount - 2\n        )\n    }\n\n    fun refreshLanguage() {\n        notifyItemChanged(0)\n    }\n\n    fun refreshFreeChoice() {\n        notifyItemChanged(1)\n    }\n\n    inner class SlideHolder(\n        itemView: View\n    ) : RecyclerView.ViewHolder(\n        itemView\n    ) {\n        private val back: ImageButton =\n            itemView.findViewById(\n                R.id.obBackButton\n            )\n\n        private val skip: TextView =\n            itemView.findViewById(\n                R.id.obSkipButton\n            )\n\n        private val progress: LinearProgressIndicator =\n            itemView.findViewById(\n                R.id.obProgress\n            )\n\n        private val mascot: ImageView =\n            itemView.findViewById(\n                R.id.obMascot\n            )\n\n        private val title: TextView =\n            itemView.findViewById(\n                R.id.obTitle\n            )\n\n        private val description: TextView =\n            itemView.findViewById(\n                R.id.obDescription\n            )\n\n        private val languageChoices: LinearLayout =\n            itemView.findViewById(\n                R.id.obLanguageChoices\n            )\n\n        private val persian: MaterialButton =\n            itemView.findViewById(\n                R.id.obPersianButton\n            )\n\n        private val english: MaterialButton =\n            itemView.findViewById(\n                R.id.obEnglishButton\n            )\n\n        private val discovery: TextView =\n            itemView.findViewById(\n                R.id.obDiscoveryStatus\n            )\n\n        private val permissionMessage: TextView =\n            itemView.findViewById(\n                R.id.obPermissionMessage\n            )\n\n        private val primary: MaterialButton =\n            itemView.findViewById(\n                R.id.obPrimaryButton\n            )\n\n        fun bind(\n            slide: OnboardingSlide,\n            position: Int\n        ) {\n            val context = itemView.context\n\n            progress.max = slides.size\n            progress.progress = position + 1\n            progress.contentDescription =\n                context.getString(\n                    R.string.ob7_progress_accessibility,\n                    position + 1,\n                    slides.size\n                )\n\n            ViewCompat.setAccessibilityLiveRegion(\n                progress,\n                ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE\n            )\n\n            back.visibility =\n                if (position > 0) View.VISIBLE else View.INVISIBLE\n\n            skip.visibility =\n                if (slide.skipAllowed) View.VISIBLE else View.INVISIBLE\n\n            mascot.setImageResource(slide.mascotRes)\n            mascot.contentDescription =\n                context.getString(slide.mascotDescriptionRes)\n\n            val screenHeight =\n                context.resources.displayMetrics.heightPixels\n            val density =\n                context.resources.displayMetrics.density\n            val desired =\n                (screenHeight * 0.35f).roundToInt()\n            val minimum =\n                (200f * density).roundToInt()\n            val maximum =\n                (360f * density).roundToInt()\n\n            mascot.layoutParams =\n                mascot.layoutParams.apply {\n                    height = desired.coerceIn(\n                        minimum,\n                        maximum\n                    )\n                }\n\n            mascot.scaleType = ImageView.ScaleType.FIT_CENTER\n\n            title.setText(slide.titleRes)\n            description.setText(slide.descriptionRes)\n            primary.setText(slide.primaryActionRes)\n\n            primary.isEnabled =\n                !actionPending &&\n                    when (position) {\n                        0 -> selectedLanguage() != null\n                        1 -> selectedFreeMode() != null\n                        else -> true\n                    }\n\n            languageChoices.visibility =\n                if (position == 0 || position == 1) View.VISIBLE else View.GONE\n\n            permissionMessage.visibility =\n                if (position == 6 && permissionDenied) {\n                    View.VISIBLE\n                } else {\n                    View.GONE\n                }\n\n            if (position == 6 && permissionDenied) {\n                primary.setText(R.string.ob7_retry_permission)\n            }\n\n            discovery.visibility =\n                if (\n                    position > 1 &&\n                    discoveryState !=\n                    OnboardingDiscoveryCoordinator.State.IDLE\n                ) {\n                    View.VISIBLE\n                } else {\n                    View.GONE\n                }\n\n            discovery.setText(\n                when (discoveryState) {\n                    OnboardingDiscoveryCoordinator.State.READY ->\n                        R.string.ob7_background_ready\n\n                    OnboardingDiscoveryCoordinator.State.FAILED ->\n                        R.string.ob7_background_retry_later\n\n                    else ->\n                        R.string.ob7_background_preparing\n                }\n            )\n\n            bindChoiceButtons(position)\n\n            back.setOnClickListener {\n                onBack(bindingAdapterPosition)\n            }\n\n            skip.setOnClickListener {\n                onSkip(bindingAdapterPosition)\n            }\n\n            primary.setOnClickListener {\n                onPrimary(bindingAdapterPosition)\n            }\n\n            persian.setOnClickListener {\n                when (position) {\n                    0 -> onLanguageSelected("fa")\n                    1 -> onFreeModeSelected(true)\n                }\n            }\n\n            english.setOnClickListener {\n                when (position) {\n                    0 -> onLanguageSelected("en")\n                    1 -> onFreeModeSelected(false)\n                }\n            }\n\n            AppTypography.apply(\n                context,\n                itemView\n            )\n\n            if (\n                position == 4 &&\n                ValueAnimator.areAnimatorsEnabled()\n            ) {\n                mascot.animate().cancel()\n                mascot.alpha = 0f\n                mascot.translationX = 10f * density\n                mascot.animate()\n                    .alpha(1f)\n                    .translationX(0f)\n                    .setDuration(280L)\n                    .start()\n            } else {\n                mascot.alpha = 1f\n                mascot.translationX = 0f\n            }\n        }\n\n        private fun bindChoiceButtons(position: Int) {\n            val context = itemView.context\n\n            if (position == 0) {\n                persian.setText(R.string.ob7_language_persian)\n                english.setText(R.string.ob7_language_english)\n\n                val language = selectedLanguage()\n\n                styleChoiceButton(\n                    button = persian,\n                    selected = language == "fa"\n                )\n\n                styleChoiceButton(\n                    button = english,\n                    selected = language == "en"\n                )\n\n                languageChoices.contentDescription =\n                    context.getString(\n                        R.string.ob7_language_group_description\n                    )\n            } else if (position == 1) {\n                persian.setText(R.string.ob7_free_service_yes)\n                english.setText(R.string.ob7_free_service_no)\n\n                val freeMode = selectedFreeMode()\n\n                styleChoiceButton(\n                    button = persian,\n                    selected = freeMode == true\n                )\n\n                styleChoiceButton(\n                    button = english,\n                    selected = freeMode == false\n                )\n\n                languageChoices.contentDescription =\n                    context.getString(\n                        R.string.ob7_free_service_group_description\n                    )\n            }\n        }\n\n        private fun styleChoiceButton(\n            button: MaterialButton,\n            selected: Boolean\n        ) {\n            val context = itemView.context\n\n            button.setBackgroundResource(\n                if (selected) {\n                    R.drawable.bg_onboarding_language_selected\n                } else {\n                    R.drawable.bg_onboarding_language_unselected\n                }\n            )\n\n            button.backgroundTintList = null\n            button.strokeWidth = 0\n\n            button.setTextColor(\n                ContextCompat.getColor(\n                    context,\n                    if (selected) {\n                        R.color.vico_premium_white\n                    } else {\n                        R.color.vico_premium_muted\n                    }\n                )\n            )\n\n            button.iconTint =\n                ColorStateList.valueOf(\n                    ContextCompat.getColor(\n                        context,\n                        if (selected) {\n                            R.color.vico_premium_orange\n                        } else {\n                            R.color.vico_premium_muted\n                        }\n                    )\n                )\n\n            button.contentDescription =\n                button.text.toString() +\n                    if (selected) {\n                        ", " +\n                            context.getString(\n                                R.string.ob7_language_selected\n                            )\n                    } else {\n                        ""\n                    }\n        }\n    }\n}\n'
ACTIVITY_KT = 'package com.vicovpn.client.onboarding\n\nimport android.app.Activity\nimport android.content.BroadcastReceiver\nimport android.content.Context\nimport android.content.Intent\nimport android.content.IntentFilter\nimport android.content.res.Configuration\nimport android.net.VpnService\nimport android.os.Build\nimport android.os.Bundle\nimport android.os.Handler\nimport android.os.Looper\nimport android.view.View\nimport androidx.activity.OnBackPressedCallback\nimport androidx.activity.result.contract.ActivityResultContracts\nimport androidx.appcompat.app.AppCompatActivity\nimport androidx.appcompat.app.AppCompatDelegate\nimport androidx.core.content.ContextCompat\nimport androidx.core.os.LocaleListCompat\nimport androidx.core.view.ViewCompat\nimport androidx.core.view.WindowCompat\nimport androidx.core.view.WindowInsetsCompat\nimport androidx.core.view.WindowInsetsControllerCompat\nimport androidx.viewpager2.widget.ViewPager2\nimport com.vicovpn.client.MainActivity\nimport com.vicovpn.client.R\nimport com.vicovpn.client.server.ServerStore\nimport com.vicovpn.client.ui.AppTypography\nimport com.vicovpn.client.vpn.VicoVpnService\n\nclass OnboardingActivity : AppCompatActivity() {\n\n    companion object {\n        const val EXTRA_FROM_ONBOARDING =\n            "from_onboarding"\n\n        private const val SETTINGS_PREFERENCES =\n            "app_settings"\n\n        private const val KEY_LANGUAGE_SELECTED =\n            "language_selected"\n\n        private const val KEY_LANGUAGE =\n            "language"\n\n        private const val KEY_THEME =\n            "theme"\n\n        private const val KEY_ONBOARDING_COMPLETE =\n            "onboarding_complete"\n\n        private const val KEY_FIRST_AUTO_CONNECT_DONE =\n            "first_auto_connect_done"\n\n        private const val KEY_DISCOVERY_STARTED =\n            "onboarding_discovery_started"\n\n        private const val KEY_FREE_SERVICE_CHOSEN =\n            "onboarding_free_service_chosen"\n\n        private const val KEY_FREE_SERVICE_ENABLED =\n            "onboarding_free_service_enabled"\n\n        private const val STATE_PAGE =\n            "page"\n\n        private const val STATE_MAX_UNLOCKED =\n            "max_unlocked"\n\n        private const val THEME_SYSTEM = "system"\n        private const val THEME_LIGHT = "light"\n        private const val THEME_DARK = "dark"\n    }\n\n    private lateinit var pager: ViewPager2\n    private lateinit var adapter: OnboardingAdapter\n\n    private var currentPage = 0\n    private var maxUnlockedPage = 0\n    private var navigationLocked = false\n    private var permissionRequestPending = false\n    private var verificationRequested = false\n    private var verificationStarted = false\n    private var routeReady = false\n\n    private val handler =\n        Handler(Looper.getMainLooper())\n\n    private val discoveryListener =\n        OnboardingDiscoveryCoordinator.Listener {\n                state ->\n            adapter.setDiscoveryState(state)\n\n            if (\n                state == OnboardingDiscoveryCoordinator.State.READY &&\n                verificationRequested\n            ) {\n                startNativeVerification()\n            }\n        }\n\n    private val vpnPermission =\n        registerForActivityResult(\n            ActivityResultContracts.StartActivityForResult()\n        ) {\n                result ->\n            permissionRequestPending = false\n            adapter.setActionPending(false)\n\n            if (result.resultCode == Activity.RESULT_OK) {\n                adapter.setPermissionDenied(false)\n                verificationRequested = true\n                startNativeVerification()\n                unlockAndMoveTo(7)\n            } else {\n                adapter.setPermissionDenied(true)\n                pager.setCurrentItem(6, true)\n            }\n        }\n\n    private val freeProgressReceiver =\n        object : BroadcastReceiver() {\n            override fun onReceive(\n                context: Context?,\n                intent: Intent?\n            ) {\n                if (intent == null) return\n\n                val working =\n                    intent.getIntExtra(\n                        VicoVpnService.EXTRA_FREE_WORKING,\n                        0\n                    )\n\n                if (working > 0 && !routeReady) {\n                    routeReady = true\n                    ServerStore(\n                        this@OnboardingActivity\n                    ).activateBestFreeServer()\n                    adapter.setDiscoveryState(\n                        OnboardingDiscoveryCoordinator.State.READY\n                    )\n                }\n            }\n        }\n\n    override fun onCreate(\n        savedInstanceState: Bundle?\n    ) {\n        restoreTheme()\n        restoreLanguage()\n        super.onCreate(savedInstanceState)\n\n        if (isOnboardingComplete()) {\n            openMain()\n            return\n        }\n\n        WindowCompat.setDecorFitsSystemWindows(\n            window,\n            false\n        )\n\n        setContentView(R.layout.activity_onboarding)\n\n        val root =\n            findViewById<View>(R.id.onboardingRoot)\n\n        AppTypography.apply(this, root)\n        applySystemBars(root)\n\n        ViewCompat.setOnApplyWindowInsetsListener(\n            root\n        ) {\n                view,\n                insets ->\n            val safe =\n                insets.getInsets(\n                    WindowInsetsCompat.Type.systemBars() or\n                        WindowInsetsCompat.Type.displayCutout()\n                )\n\n            view.setPadding(\n                safe.left,\n                safe.top,\n                safe.right,\n                safe.bottom\n            )\n\n            insets\n        }\n\n        pager = findViewById(R.id.onboardingPager)\n\n        currentPage =\n            savedInstanceState?.getInt(\n                STATE_PAGE,\n                0\n            ) ?: 0\n\n        maxUnlockedPage =\n            savedInstanceState?.getInt(\n                STATE_MAX_UNLOCKED,\n                currentPage\n            ) ?: currentPage\n\n        adapter =\n            OnboardingAdapter(\n                slides = OnboardingSlides.items,\n                selectedLanguage = {\n                    selectedLanguage()\n                },\n                selectedFreeMode = {\n                    selectedFreeMode()\n                },\n                onLanguageSelected = {\n                        language ->\n                    selectLanguage(language)\n                },\n                onFreeModeSelected = {\n                        enabled ->\n                    selectFreeMode(enabled)\n                },\n                onPrimary = {\n                        page ->\n                    handlePrimary(page)\n                },\n                onBack = {\n                        page ->\n                    goBack(page)\n                },\n                onSkip = {\n                    skipInformationalSlides()\n                }\n            )\n\n        pager.adapter = adapter\n        pager.offscreenPageLimit = 1\n\n        pager.setPageTransformer {\n                page,\n                position ->\n            if (\n                android.animation.ValueAnimator.areAnimatorsEnabled()\n            ) {\n                val absolute =\n                    kotlin.math.abs(\n                        position\n                    ).coerceIn(\n                        0f,\n                        1f\n                    )\n\n                val eased =\n                    1f -\n                        (\n                            1f -\n                                absolute\n                            ) *\n                            (\n                                1f -\n                                    absolute\n                                )\n\n                page.alpha =\n                    (\n                        1f -\n                            eased *\n                            0.42f\n                        ).coerceIn(\n                        0.58f,\n                        1f\n                    )\n\n                val scale =\n                    1f -\n                        eased *\n                        0.075f\n\n                page.scaleX = scale\n                page.scaleY = scale\n                page.translationX =\n                    -position *\n                        page.width *\n                        0.065f\n                page.translationY =\n                    eased *\n                        12f *\n                        resources.displayMetrics.density\n                page.rotationY =\n                    position * -1.35f\n                page.cameraDistance =\n                    18_000f *\n                        resources.displayMetrics.density\n            } else {\n                page.alpha = 1f\n                page.scaleX = 1f\n                page.scaleY = 1f\n                page.translationX = 0f\n                page.translationY = 0f\n                page.rotationY = 0f\n            }\n        }\n\n        pager.registerOnPageChangeCallback(\n            object : ViewPager2.OnPageChangeCallback() {\n                override fun onPageSelected(\n                    position: Int\n                ) {\n                    if (position > maxUnlockedPage) {\n                        pager.post {\n                            pager.setCurrentItem(\n                                maxUnlockedPage,\n                                false\n                            )\n                        }\n                        return\n                    }\n\n                    currentPage = position\n                }\n            }\n        )\n\n        pager.setCurrentItem(\n            currentPage.coerceIn(\n                0,\n                maxUnlockedPage\n            ),\n            false\n        )\n\n        OnboardingDiscoveryCoordinator.addListener(\n            discoveryListener\n        )\n\n        if (\n            getPreferencesStore().getBoolean(\n                KEY_DISCOVERY_STARTED,\n                false\n            )\n        ) {\n            beginBackgroundPreparation()\n        }\n\n        onBackPressedDispatcher.addCallback(\n            this,\n            object : OnBackPressedCallback(true) {\n                override fun handleOnBackPressed() {\n                    if (currentPage > 0) {\n                        goBack(currentPage)\n                    } else {\n                        finishAffinity()\n                    }\n                }\n            }\n        )\n    }\n\n    override fun onStart() {\n        super.onStart()\n\n        val filter =\n            IntentFilter(\n                VicoVpnService.ACTION_FREE_TEST_PROGRESS\n            )\n\n        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n            registerReceiver(\n                freeProgressReceiver,\n                filter,\n                Context.RECEIVER_NOT_EXPORTED\n            )\n        } else {\n            @Suppress("DEPRECATION")\n            registerReceiver(\n                freeProgressReceiver,\n                filter\n            )\n        }\n    }\n\n    override fun onStop() {\n        runCatching {\n            unregisterReceiver(freeProgressReceiver)\n        }\n        super.onStop()\n    }\n\n    override fun onDestroy() {\n        OnboardingDiscoveryCoordinator.removeListener(\n            discoveryListener\n        )\n        handler.removeCallbacksAndMessages(null)\n        super.onDestroy()\n    }\n\n    override fun onSaveInstanceState(\n        outState: Bundle\n    ) {\n        outState.putInt(STATE_PAGE, currentPage)\n        outState.putInt(\n            STATE_MAX_UNLOCKED,\n            maxUnlockedPage\n        )\n        super.onSaveInstanceState(outState)\n    }\n\n    private fun handlePrimary(page: Int) {\n        if (navigationLocked) return\n\n        when (page) {\n            0 -> {\n                if (selectedLanguage() == null) return\n                unlockAndMoveTo(1)\n            }\n\n            1 -> {\n                val useFree = selectedFreeMode() ?: return\n                if (useFree) {\n                    beginBackgroundPreparation()\n                }\n                unlockAndMoveTo(2)\n            }\n\n            in 2..5 ->\n                unlockAndMoveTo(page + 1)\n\n            6 ->\n                requestVpnPermission()\n\n            7 ->\n                finishOnboarding()\n        }\n    }\n\n    private fun requestVpnPermission() {\n        if (permissionRequestPending) return\n\n        permissionRequestPending = true\n        adapter.setActionPending(true)\n\n        val prepareIntent = VpnService.prepare(this)\n\n        if (prepareIntent == null) {\n            permissionRequestPending = false\n            adapter.setActionPending(false)\n            adapter.setPermissionDenied(false)\n            verificationRequested = true\n            startNativeVerification()\n            unlockAndMoveTo(7)\n        } else {\n            vpnPermission.launch(prepareIntent)\n        }\n    }\n\n    private fun startNativeVerification() {\n        if (\n            verificationStarted ||\n            OnboardingDiscoveryCoordinator.state() !=\n            OnboardingDiscoveryCoordinator.State.READY\n        ) {\n            return\n        }\n\n        verificationStarted = true\n\n        ContextCompat.startForegroundService(\n            this,\n            Intent(\n                this,\n                VicoVpnService::class.java\n            ).setAction(\n                VicoVpnService.ACTION_TEST_FREE_SERVERS\n            )\n        )\n    }\n\n    private fun beginBackgroundPreparation() {\n        getPreferencesStore()\n            .edit()\n            .putBoolean(\n                KEY_DISCOVERY_STARTED,\n                true\n            )\n            .apply()\n\n        OnboardingDiscoveryCoordinator.start(\n            applicationContext\n        )\n    }\n\n    private fun unlockAndMoveTo(\n        targetPage: Int\n    ) {\n        if (navigationLocked) return\n\n        navigationLocked = true\n        maxUnlockedPage =\n            maxUnlockedPage.coerceAtLeast(targetPage)\n        pager.setCurrentItem(targetPage, true)\n\n        handler.postDelayed(\n            {\n                navigationLocked = false\n            },\n            300L\n        )\n    }\n\n    private fun goBack(fromPage: Int) {\n        if (navigationLocked || fromPage <= 0) return\n\n        navigationLocked = true\n        pager.setCurrentItem(fromPage - 1, true)\n\n        handler.postDelayed(\n            {\n                navigationLocked = false\n            },\n            280L\n        )\n    }\n\n    private fun skipInformationalSlides() {\n        if (currentPage !in 2..5) return\n\n        maxUnlockedPage =\n            maxUnlockedPage.coerceAtLeast(6)\n        pager.setCurrentItem(6, true)\n    }\n\n    private fun selectLanguage(languageTag: String) {\n        require(\n            languageTag == "fa" ||\n                languageTag == "en"\n        )\n\n        getPreferencesStore()\n            .edit()\n            .putBoolean(\n                KEY_LANGUAGE_SELECTED,\n                true\n            )\n            .putString(KEY_LANGUAGE, languageTag)\n            .apply()\n\n        adapter.refreshLanguage()\n\n        val current =\n            AppCompatDelegate.getApplicationLocales()\n                .toLanguageTags()\n\n        if (current != languageTag) {\n            AppCompatDelegate.setApplicationLocales(\n                LocaleListCompat.forLanguageTags(\n                    languageTag\n                )\n            )\n        }\n    }\n\n    private fun selectedLanguage(): String? {\n        val preferences = getPreferencesStore()\n\n        if (\n            !preferences.getBoolean(\n                KEY_LANGUAGE_SELECTED,\n                false\n            )\n        ) {\n            return null\n        }\n\n        return preferences.getString(\n            KEY_LANGUAGE,\n            null\n        )?.takeIf {\n            it == "fa" || it == "en"\n        }\n    }\n\n    private fun selectFreeMode(enabled: Boolean) {\n        getPreferencesStore()\n            .edit()\n            .putBoolean(\n                KEY_FREE_SERVICE_CHOSEN,\n                true\n            )\n            .putBoolean(\n                KEY_FREE_SERVICE_ENABLED,\n                enabled\n            )\n            .apply()\n\n        adapter.refreshFreeChoice()\n    }\n\n    private fun selectedFreeMode(): Boolean? {\n        val preferences = getPreferencesStore()\n\n        if (\n            !preferences.getBoolean(\n                KEY_FREE_SERVICE_CHOSEN,\n                false\n            )\n        ) {\n            return null\n        }\n\n        return preferences.getBoolean(\n            KEY_FREE_SERVICE_ENABLED,\n            false\n        )\n    }\n\n    private fun finishOnboarding() {\n        getPreferencesStore()\n            .edit()\n            .putBoolean(\n                KEY_ONBOARDING_COMPLETE,\n                true\n            )\n            .putBoolean(\n                KEY_FIRST_AUTO_CONNECT_DONE,\n                true\n            )\n            .putBoolean(\n                KEY_LANGUAGE_SELECTED,\n                true\n            )\n            .apply()\n\n        openMain()\n    }\n\n    private fun openMain() {\n        startActivity(\n            Intent(\n                this,\n                MainActivity::class.java\n            ).putExtra(\n                EXTRA_FROM_ONBOARDING,\n                true\n            )\n        )\n        finish()\n    }\n\n    private fun isOnboardingComplete(): Boolean =\n        getPreferencesStore().getBoolean(\n            KEY_ONBOARDING_COMPLETE,\n            false\n        )\n\n    private fun getPreferencesStore() =\n        getSharedPreferences(\n            SETTINGS_PREFERENCES,\n            Context.MODE_PRIVATE\n        )\n\n    private fun restoreLanguage() {\n        val language =\n            getPreferencesStore().getString(\n                KEY_LANGUAGE,\n                null\n            ) ?: return\n\n        AppCompatDelegate.setApplicationLocales(\n            LocaleListCompat.forLanguageTags(language)\n        )\n    }\n\n    private fun restoreTheme() {\n        val theme =\n            getPreferencesStore().getString(\n                KEY_THEME,\n                THEME_DARK\n            ) ?: THEME_DARK\n\n        AppCompatDelegate.setDefaultNightMode(\n            when (theme) {\n                THEME_SYSTEM ->\n                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM\n\n                THEME_LIGHT ->\n                    AppCompatDelegate.MODE_NIGHT_NO\n\n                else ->\n                    AppCompatDelegate.MODE_NIGHT_YES\n            }\n        )\n    }\n\n    private fun applySystemBars(root: View) {\n        val night =\n            resources.configuration.uiMode and\n                Configuration.UI_MODE_NIGHT_MASK ==\n                Configuration.UI_MODE_NIGHT_YES\n\n        window.statusBarColor =\n            ContextCompat.getColor(\n                this,\n                R.color.vico_premium_background\n            )\n\n        window.navigationBarColor =\n            ContextCompat.getColor(\n                this,\n                R.color.vico_premium_background\n            )\n\n        WindowInsetsControllerCompat(\n            window,\n            root\n        ).apply {\n            isAppearanceLightStatusBars = !night\n            isAppearanceLightNavigationBars = !night\n        }\n    }\n}\n'
PIXEL_UPLOAD = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M10,13H6V9H10V5H14V9H18V13H14V17H10ZM4,18H20V22H4Z" />\n</vector>\n'
PIXEL_DOWNLOAD = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M10,3H14V11H18V15H14V19H10V15H6V11H10ZM4,20H20V23H4Z" />\n</vector>\n'
PIXEL_POWER = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="32dp"\n    android:height="32dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M10,2H14V12H10ZM5,5H9V8H7V16H9V19H15V16H17V8H15V5H19V7H21V17H19V20H16V22H8V20H5V17H3V7H5Z" />\n</vector>\n'
PIXEL_CHEVRON = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="20dp"\n    android:height="20dp"\n    android:viewportWidth="24"\n    android:viewportHeight="24">\n    <path\n        android:fillColor="#FFFFFFFF"\n        android:pathData="M12,4H16V8H12V12H8V16H12V20H8V16H4V8H8V4Z" />\n</vector>\n'
EN_STRINGS = {'ob7_free_service_title': 'Use VicoVPN free services?', 'ob7_free_service_description': 'If you enable free services, VicoVPN can prepare free routes in the background and use both premium and free sources when needed.', 'ob7_free_service_yes': 'Use free + premium', 'ob7_free_service_no': 'Premium only', 'ob7_free_service_group_description': 'Choose whether free services should be enabled during setup'}
FA_STRINGS = {'app_name': 'ویکو وی پی ان', 'ob7_free_service_title': 'می\u200cخواهید از سرویس\u200cهای رایگان ویکو وی پی ان استفاده کنید؟', 'ob7_free_service_description': 'اگر این گزینه را فعال کنید، ویکو وی پی ان در پس\u200cزمینه مسیرهای رایگان را آماده می\u200cکند و در کنار مسیرهای ویژه از آن\u200cها استفاده می\u200cکند.', 'ob7_free_service_yes': 'رایگان + ویژه', 'ob7_free_service_no': 'فقط ویژه', 'ob7_free_service_group_description': 'انتخاب کنید سرویس\u200cهای رایگان در هنگام راه\u200cاندازی فعال شوند یا نه'}

ET.register_namespace('android', NS_ANDROID)
ET.register_namespace('app', NS_APP)

def fail(message):
    print("\nERROR: " + str(message), file=sys.stderr)
    raise SystemExit(1)

def read_text(path):
    return path.read_text(encoding="utf-8-sig")

def write_text(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.rstrip() + "\n", encoding="utf-8")
    print("Updated:", path.relative_to(ROOT))

def ensure_project():
    required = [ROOT / "gradlew.bat", STRINGS_EN, STRINGS_FA, MAIN_ACTIVITY]
    for path in required:
        if not path.exists():
            fail("Run this script from C:\\AndroidProjects\\VicoVPN. Missing: " + str(path))

def make_backup(paths):
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    backup = BACKUP_ROOT / stamp
    backup.mkdir(parents=True, exist_ok=False)
    state = {"backup": str(backup.relative_to(ROOT)), "files": {}}
    for path in paths:
        relative = str(path.relative_to(ROOT))
        existed = path.exists()
        state["files"][relative] = {"existed": existed}
        if existed:
            destination = backup / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)
    STATE_FILE.write_text(json.dumps(state, indent=2), encoding="utf-8")
    return backup

def rollback():
    if not STATE_FILE.exists():
        fail("No backup state was found for this patch.")
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
            target.unlink()
    STATE_FILE.unlink()
    print("Rollback complete:", backup)

def xml_escape(value):
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;").replace("'", "&apos;")

def upsert_strings(path, mapping):
    text = read_text(path)
    for key, value in mapping.items():
        pattern = r'<string\s+name="' + re.escape(key) + r'"[^>]*>.*?</string>'
        entry = '    <string name="' + key + '">' + xml_escape(value) + '</string>'
        if re.search(pattern, text, flags=re.DOTALL):
            text = re.sub(pattern, entry, text, count=1, flags=re.DOTALL)
        else:
            text = text.replace('</resources>', entry + '\n</resources>')
    if path == STRINGS_FA:
        text = text.replace("VicoVPN", "ویکو وی پی ان").replace("Vicovpn", "ویکو وی پی ان")
    write_text(path, text)

def write_drawables():
    write_text(DRAWABLE_UPLOAD, PIXEL_UPLOAD)
    write_text(DRAWABLE_DOWNLOAD, PIXEL_DOWNLOAD)
    write_text(DRAWABLE_POWER, PIXEL_POWER)
    write_text(DRAWABLE_CHEVRON, PIXEL_CHEVRON)

def patch_onboarding_files():
    if not ONBOARDING_DIR.exists():
        fail("Onboarding directory was not found: " + str(ONBOARDING_DIR))
    write_text(ONBOARDING_MODEL, MODEL_KT)
    write_text(ONBOARDING_ADAPTER, ADAPTER_KT)
    write_text(ONBOARDING_ACTIVITY, ACTIVITY_KT)

def patch_main_activity_placeholders():
    text = read_text(MAIN_ACTIVITY)
    if "private fun suppressPostOnboardingPlaceholders()" not in text:
        if "import android.widget.TextView" not in text:
            if "import android.widget.Toast\n" in text:
                text = text.replace("import android.widget.Toast\n", "import android.widget.Toast\nimport android.widget.TextView\n")
            else:
                last_import = text.rfind("import ")
                if last_import != -1:
                    line_end = text.find("\n", last_import)
                    text = text[:line_end + 1] + "import android.widget.TextView\n" + text[line_end + 1:]
        text = text.rstrip()
        insert = '\n\n    private fun suppressPostOnboardingPlaceholders() {\n        val preferences = getSharedPreferences(\n            "app_settings",\n            MODE_PRIVATE\n        )\n\n        if (!preferences.getBoolean("onboarding_complete", false)) {\n            return\n        }\n\n        val candidates = listOf(\n            "subscriptionLastResultText",\n            "serviceEmptyText",\n            "servicesEmptyText",\n            "noServicesText",\n            "noServiceText",\n            "serverSubtitleText",\n            "connectSubtitleText",\n            "discoveryMiniText",\n            "helperText"\n        )\n\n        candidates.forEach { name ->\n            val id = resources.getIdentifier(name, "id", packageName)\n            if (id == 0) return@forEach\n            val view = findViewById<View>(id) ?: return@forEach\n            val message = (view as? TextView)?.text?.toString()?.trim().orEmpty()\n            if (\n                message.isBlank() ||\n                message.equals("no services yet", ignoreCase = true) ||\n                message.contains("service", ignoreCase = true) ||\n                message.contains("سرویسی", ignoreCase = true) ||\n                message.contains("هنوز", ignoreCase = true)\n            ) {\n                view.visibility = View.GONE\n            }\n        }\n    }\n'
        if text.endswith("}"):
            text = text[:-1] + insert + "\n}\n"
    prefix = text.split("private fun suppressPostOnboardingPlaceholders()", 1)[0]
    if "suppressPostOnboardingPlaceholders()" not in prefix:
        text = re.sub(r"(setContentView\([^\n]+\)\n)", r"\1\n        window.decorView.post {\n            suppressPostOnboardingPlaceholders()\n        }\n", text, count=1)
    write_text(MAIN_ACTIVITY, text)

def patch_xml_icons(path):
    if not path.exists():
        return
    tree = ET.parse(path)
    root = tree.getroot()
    changed = False
    for element in root.iter():
        view_id = element.attrib.get("{" + NS_ANDROID + "}id", "")
        lowered = view_id.lower()
        drawable = None
        if "upload" in lowered:
            drawable = "@drawable/ic_upload_pixel"
        elif "download" in lowered:
            drawable = "@drawable/ic_download_pixel"
        elif "connect" in lowered or "power" in lowered:
            drawable = "@drawable/ic_power_pixel"
        elif any(token in lowered for token in ["vip", "priority", "chevron", "arrow"]):
            drawable = "@drawable/ic_chevron_pixel"
        if not drawable:
            continue
        for attr in ["{" + NS_ANDROID + "}src", "{" + NS_APP + "}srcCompat", "{" + NS_APP + "}icon"]:
            if attr in element.attrib and element.attrib[attr] != drawable:
                element.attrib[attr] = drawable
                changed = True
    if changed:
        path.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode"), encoding="utf-8")
        print("Updated:", path.relative_to(ROOT))

def collect_paths():
    paths = [
        STRINGS_EN,
        STRINGS_FA,
        MAIN_ACTIVITY,
        MAIN_LAYOUT,
        ONBOARDING_MODEL,
        ONBOARDING_ADAPTER,
        ONBOARDING_ACTIVITY,
        DRAWABLE_UPLOAD,
        DRAWABLE_DOWNLOAD,
        DRAWABLE_POWER,
        DRAWABLE_CHEVRON,
    ]
    if VIP_LAYOUT.exists():
        paths.append(VIP_LAYOUT)
    return paths

def run_gradle(install=False):
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        fallback = Path(r"C:\Program Files\Android\Android Studio\jbr")
        if fallback.exists():
            java_home = str(fallback)
    env = os.environ.copy()
    if java_home:
        env["JAVA_HOME"] = java_home
        env["PATH"] = str(Path(java_home) / "bin") + os.pathsep + env.get("PATH", "")
    task = "installDebug" if install else "assembleDebug"
    command = [str(ROOT / "gradlew.bat"), "clean", task, "--no-daemon"]
    print("\nUsing JAVA_HOME:", env.get("JAVA_HOME", "<not set>"))
    print("Running:", " ".join(command))
    result = subprocess.run(command, cwd=str(ROOT), env=env, check=False)
    if result.returncode != 0:
        print("\nThe patch was applied, but Gradle reported another issue.", file=sys.stderr)
        print("Rollback with:\n  python apply_vicovpn_main_icons_free_service_slide.py --rollback", file=sys.stderr)
        raise SystemExit(result.returncode)
    print("\nBUILD SUCCESSFUL")
    if not install:
        print("APK:", ROOT / "app/build/outputs/apk/debug/app-debug.apk")

def apply_patch(no_build=False, install=False):
    ensure_project()
    backup = make_backup(collect_paths())
    print("Backup:", backup)
    write_drawables()
    upsert_strings(STRINGS_EN, EN_STRINGS)
    upsert_strings(STRINGS_FA, FA_STRINGS)
    patch_onboarding_files()
    patch_main_activity_placeholders()
    patch_xml_icons(MAIN_LAYOUT)
    patch_xml_icons(VIP_LAYOUT)
    print("\nApplied:")
    print("- pixel-style connect, upload, download, and priority/VIP icons on the main screens")
    print("- dedicated free-service onboarding slide right after language selection")
    print("- smoother onboarding page transitions")
    print("- Persian brand text switched to 'ویکو وی پی ان' in Persian resources")
    print("- post-onboarding placeholder text under the connect area suppressed when empty")
    if not no_build:
        run_gradle(install=install)
    else:
        print("\nBuild skipped. Run:")
        print("  .\\gradlew.bat clean assembleDebug --no-daemon")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rollback", action="store_true")
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--install", action="store_true")
    args = parser.parse_args()
    if args.rollback:
        rollback()
        return
    apply_patch(no_build=args.no_build, install=args.install)

if __name__ == "__main__":
    main()

