package com.vicovpn.client.onboarding

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager2.widget.ViewPager2
import com.vicovpn.client.MainActivity
import com.vicovpn.client.R
import com.vicovpn.client.server.ServerStore
import com.vicovpn.client.ui.AppTypography
import com.vicovpn.client.vpn.VicoVpnService

class OnboardingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_ONBOARDING =
            "from_onboarding"

        private const val SETTINGS_PREFERENCES =
            "app_settings"

        private const val KEY_LANGUAGE_SELECTED =
            "language_selected"

        private const val KEY_LANGUAGE =
            "language"

        private const val KEY_THEME =
            "theme"

        private const val KEY_ONBOARDING_COMPLETE =
            "onboarding_complete"

        private const val KEY_FIRST_AUTO_CONNECT_DONE =
            "first_auto_connect_done"

        private const val KEY_DISCOVERY_STARTED =
            "onboarding_discovery_started"

        private const val KEY_FREE_SERVICE_CHOSEN =
            "onboarding_free_service_chosen"

        private const val KEY_FREE_SERVICE_ENABLED =
            "onboarding_free_service_enabled"

        private const val STATE_PAGE =
            "page"

        private const val STATE_MAX_UNLOCKED =
            "max_unlocked"

        private const val THEME_SYSTEM = "system"
        private const val THEME_LIGHT = "light"
        private const val THEME_DARK = "dark"
    }

    private lateinit var pager: ViewPager2
    private lateinit var adapter: OnboardingAdapter

    private var currentPage = 0
    private var maxUnlockedPage = 0
    private var navigationLocked = false
    private var permissionRequestPending = false
    private var verificationRequested = false
    private var verificationStarted = false
    private var routeReady = false

    private val handler =
        Handler(Looper.getMainLooper())

    private val discoveryListener =
        OnboardingDiscoveryCoordinator.Listener {
                state ->
            adapter.setDiscoveryState(state)

            if (
                state == OnboardingDiscoveryCoordinator.State.READY &&
                verificationRequested
            ) {
                startNativeVerification()
            }
        }

    private val vpnPermission =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
                result ->
            permissionRequestPending = false
            adapter.setActionPending(false)

            if (result.resultCode == Activity.RESULT_OK) {
                adapter.setPermissionDenied(false)
                verificationRequested = true
                startNativeVerification()
                unlockAndMoveTo(7)
            } else {
                adapter.setPermissionDenied(true)
                pager.setCurrentItem(6, true)
            }
        }

    private val freeProgressReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (intent == null) return

                val working =
                    intent.getIntExtra(
                        VicoVpnService.EXTRA_FREE_WORKING,
                        0
                    )

                if (working > 0 && !routeReady) {
                    routeReady = true
                    ServerStore(
                        this@OnboardingActivity
                    ).activateBestFreeServer()
                    adapter.setDiscoveryState(
                        OnboardingDiscoveryCoordinator.State.READY
                    )
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        restoreTheme()
        restoreLanguage()
        super.onCreate(savedInstanceState)

        if (isOnboardingComplete()) {
            openMain()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        setContentView(R.layout.activity_onboarding)

        val root =
            findViewById<View>(R.id.onboardingRoot)

        AppTypography.apply(this, root)
        applySystemBars(root)

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) {
                view,
                insets ->
            val safe =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
                )

            view.setPadding(
                safe.left,
                safe.top,
                safe.right,
                safe.bottom
            )

            insets
        }

        pager = findViewById(R.id.onboardingPager)

        currentPage =
            savedInstanceState?.getInt(
                STATE_PAGE,
                0
            ) ?: 0

        maxUnlockedPage =
            savedInstanceState?.getInt(
                STATE_MAX_UNLOCKED,
                currentPage
            ) ?: currentPage

        adapter =
            OnboardingAdapter(
                slides = OnboardingSlides.items,
                selectedLanguage = {
                    selectedLanguage()
                },
                selectedFreeMode = {
                    selectedFreeMode()
                },
                onLanguageSelected = {
                        language ->
                    selectLanguage(language)
                },
                onFreeModeSelected = {
                        enabled ->
                    selectFreeMode(enabled)
                },
                onPrimary = {
                        page ->
                    handlePrimary(page)
                },
                onBack = {
                        page ->
                    goBack(page)
                },
                onSkip = {
                    skipInformationalSlides()
                }
            )

        pager.adapter = adapter
        pager.offscreenPageLimit = 1

        pager.setPageTransformer {
                page,
                position ->
            if (
                android.animation.ValueAnimator.areAnimatorsEnabled()
            ) {
                val absolute =
                    kotlin.math.abs(
                        position
                    ).coerceIn(
                        0f,
                        1f
                    )

                val eased =
                    1f -
                        (
                            1f -
                                absolute
                            ) *
                            (
                                1f -
                                    absolute
                                )

                page.alpha =
                    (
                        1f -
                            eased *
                            0.42f
                        ).coerceIn(
                        0.58f,
                        1f
                    )

                val scale =
                    1f -
                        eased *
                        0.075f

                page.scaleX = scale
                page.scaleY = scale
                page.translationX =
                    -position *
                        page.width *
                        0.065f
                page.translationY =
                    eased *
                        12f *
                        resources.displayMetrics.density
                page.rotationY =
                    position * -1.35f
                page.cameraDistance =
                    18_000f *
                        resources.displayMetrics.density
            } else {
                page.alpha = 1f
                page.scaleX = 1f
                page.scaleY = 1f
                page.translationX = 0f
                page.translationY = 0f
                page.rotationY = 0f
            }
        }

        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(
                    position: Int
                ) {
                    if (position > maxUnlockedPage) {
                        pager.post {
                            pager.setCurrentItem(
                                maxUnlockedPage,
                                false
                            )
                        }
                        return
                    }

                    currentPage = position
                }
            }
        )

        pager.setCurrentItem(
            currentPage.coerceIn(
                0,
                maxUnlockedPage
            ),
            false
        )

        OnboardingDiscoveryCoordinator.addListener(
            discoveryListener
        )

        if (
            getPreferencesStore().getBoolean(
                KEY_DISCOVERY_STARTED,
                false
            )
        ) {
            beginBackgroundPreparation()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentPage > 0) {
                        goBack(currentPage)
                    } else {
                        finishAffinity()
                    }
                }
            }
        )
    }

    override fun onStart() {
        super.onStart()

        val filter =
            IntentFilter(
                VicoVpnService.ACTION_FREE_TEST_PROGRESS
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                freeProgressReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(
                freeProgressReceiver,
                filter
            )
        }
    }

    override fun onStop() {
        runCatching {
            unregisterReceiver(freeProgressReceiver)
        }
        super.onStop()
    }

    override fun onDestroy() {
        OnboardingDiscoveryCoordinator.removeListener(
            discoveryListener
        )
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putInt(STATE_PAGE, currentPage)
        outState.putInt(
            STATE_MAX_UNLOCKED,
            maxUnlockedPage
        )
        super.onSaveInstanceState(outState)
    }

    private fun handlePrimary(page: Int) {
        if (navigationLocked) return

        when (page) {
            0 -> {
                if (selectedLanguage() == null) return
                unlockAndMoveTo(1)
            }

            1 -> {
                val useFree = selectedFreeMode() ?: return
                if (useFree) {
                    beginBackgroundPreparation()
                }
                unlockAndMoveTo(2)
            }

            in 2..5 ->
                unlockAndMoveTo(page + 1)

            6 ->
                requestVpnPermission()

            7 ->
                finishOnboarding()
        }
    }

    private fun requestVpnPermission() {
        if (permissionRequestPending) return

        permissionRequestPending = true
        adapter.setActionPending(true)

        val prepareIntent = VpnService.prepare(this)

        if (prepareIntent == null) {
            permissionRequestPending = false
            adapter.setActionPending(false)
            adapter.setPermissionDenied(false)
            verificationRequested = true
            startNativeVerification()
            unlockAndMoveTo(7)
        } else {
            vpnPermission.launch(prepareIntent)
        }
    }

    private fun startNativeVerification() {
        if (
            verificationStarted ||
            OnboardingDiscoveryCoordinator.state() !=
            OnboardingDiscoveryCoordinator.State.READY
        ) {
            return
        }

        verificationStarted = true

        ContextCompat.startForegroundService(
            this,
            Intent(
                this,
                VicoVpnService::class.java
            ).setAction(
                VicoVpnService.ACTION_TEST_FREE_SERVERS
            )
        )
    }

    private fun beginBackgroundPreparation() {
        getPreferencesStore()
            .edit()
            .putBoolean(
                KEY_DISCOVERY_STARTED,
                true
            )
            .apply()

        OnboardingDiscoveryCoordinator.start(
            applicationContext
        )
    }

    private fun unlockAndMoveTo(
        targetPage: Int
    ) {
        if (navigationLocked) return

        navigationLocked = true
        maxUnlockedPage =
            maxUnlockedPage.coerceAtLeast(targetPage)
        pager.setCurrentItem(targetPage, true)

        handler.postDelayed(
            {
                navigationLocked = false
            },
            300L
        )
    }

    private fun goBack(fromPage: Int) {
        if (navigationLocked || fromPage <= 0) return

        navigationLocked = true
        pager.setCurrentItem(fromPage - 1, true)

        handler.postDelayed(
            {
                navigationLocked = false
            },
            280L
        )
    }

    private fun skipInformationalSlides() {
        if (currentPage !in 2..5) return

        maxUnlockedPage =
            maxUnlockedPage.coerceAtLeast(6)
        pager.setCurrentItem(6, true)
    }

    private fun selectLanguage(languageTag: String) {
        require(
            languageTag == "fa" ||
                languageTag == "en"
        )

        getPreferencesStore()
            .edit()
            .putBoolean(
                KEY_LANGUAGE_SELECTED,
                true
            )
            .putString(KEY_LANGUAGE, languageTag)
            .apply()

        adapter.refreshLanguage()

        val current =
            AppCompatDelegate.getApplicationLocales()
                .toLanguageTags()

        if (current != languageTag) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(
                    languageTag
                )
            )
        }
    }

    private fun selectedLanguage(): String? {
        val preferences = getPreferencesStore()

        if (
            !preferences.getBoolean(
                KEY_LANGUAGE_SELECTED,
                false
            )
        ) {
            return null
        }

        return preferences.getString(
            KEY_LANGUAGE,
            null
        )?.takeIf {
            it == "fa" || it == "en"
        }
    }

    private fun selectFreeMode(enabled: Boolean) {
        getPreferencesStore()
            .edit()
            .putBoolean(
                KEY_FREE_SERVICE_CHOSEN,
                true
            )
            .putBoolean(
                KEY_FREE_SERVICE_ENABLED,
                enabled
            )
            .apply()

        adapter.refreshFreeChoice()
    }

    private fun selectedFreeMode(): Boolean? {
        val preferences = getPreferencesStore()

        if (
            !preferences.getBoolean(
                KEY_FREE_SERVICE_CHOSEN,
                false
            )
        ) {
            return null
        }

        return preferences.getBoolean(
            KEY_FREE_SERVICE_ENABLED,
            false
        )
    }

    private fun finishOnboarding() {
        getPreferencesStore()
            .edit()
            .putBoolean(
                KEY_ONBOARDING_COMPLETE,
                true
            )
            .putBoolean(
                KEY_FIRST_AUTO_CONNECT_DONE,
                true
            )
            .putBoolean(
                KEY_LANGUAGE_SELECTED,
                true
            )
            .apply()

        openMain()
    }

    private fun openMain() {
        startActivity(
            Intent(
                this,
                MainActivity::class.java
            ).putExtra(
                EXTRA_FROM_ONBOARDING,
                true
            )
        )
        finish()
    }

    private fun isOnboardingComplete(): Boolean =
        getPreferencesStore().getBoolean(
            KEY_ONBOARDING_COMPLETE,
            false
        )

    private fun getPreferencesStore() =
        getSharedPreferences(
            SETTINGS_PREFERENCES,
            Context.MODE_PRIVATE
        )

    private fun restoreLanguage() {
        val language =
            getPreferencesStore().getString(
                KEY_LANGUAGE,
                null
            ) ?: return

        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language)
        )
    }

    private fun restoreTheme() {
        val theme =
            getPreferencesStore().getString(
                KEY_THEME,
                THEME_DARK
            ) ?: THEME_DARK

        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                THEME_SYSTEM ->
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

                THEME_LIGHT ->
                    AppCompatDelegate.MODE_NIGHT_NO

                else ->
                    AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }

    private fun applySystemBars(root: View) {
        val night =
            resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor =
            ContextCompat.getColor(
                this,
                R.color.vico_premium_background
            )

        window.navigationBarColor =
            ContextCompat.getColor(
                this,
                R.color.vico_premium_background
            )

        WindowInsetsControllerCompat(
            window,
            root
        ).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }
}
