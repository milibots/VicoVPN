package com.vicovpn.client.profile

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vicovpn.client.R
import com.vicovpn.client.model.ProxyProfile
import com.vicovpn.client.parser.ShareLinkParser
import com.vicovpn.client.server.SavedServer
import com.vicovpn.client.server.ServerOrigin
import com.vicovpn.client.server.ServerStore
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import com.vicovpn.client.profile.VipRouteOptimizer
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.view.animation.DecelerateInterpolator
import com.vicovpn.client.ui.AppTypography

class VipProfileActivity :
    AppCompatActivity() {

    companion object {
        private const val PREFERENCES =
            "vip_profile"

        private const val KEY_SUBSCRIPTION =
            "subscription_key"

        private const val KEY_DISMISSED =
            "dismissed_banners"
    }

    private val worker =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private lateinit var api:
        SubscriptionApiClient

    private lateinit var serverStore:
        ServerStore

    private lateinit var keyCard:
        View

    private lateinit var loadingView:
        View

    private lateinit var errorCard:
        View

    private lateinit var errorText:
        TextView

    private lateinit var dashboardCard:
        View

    private lateinit var planTitle:
        TextView

    private lateinit var planSubtitle:
        TextView

    private lateinit var statusText:
        TextView

    private lateinit var expiryText:
        TextView

    private lateinit var usageProgress:
        LinearProgressIndicator

    private lateinit var usedTraffic:
        TextView

    private lateinit var totalTraffic:
        TextView

    private lateinit var remainingTraffic:
        TextView

    private lateinit var bannersTitle:
        View

    private lateinit var bannersContainer:
        LinearLayout

    private lateinit var keyInput:
        TextInputEditText

    private lateinit var openButton:
        MaterialButton

    private lateinit var retryButton:
        MaterialButton

    private lateinit var refreshButton:
        MaterialButton

    private lateinit var syncButton:
        MaterialButton

    private lateinit var removeButton:
        MaterialButton

    private var currentResponse:
        VipSubscriptionResponse? = null

    private var activeKey =
        ""

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        setContentView(
            R.layout.activity_vip_profile
        )
        AppTypography.apply(
            this,
            findViewById<View>(
                R.id.vipProfileRoot
            )
        )

        api =
            SubscriptionApiClient()

        serverStore =
            ServerStore(this)

        bindViews()
        configureInsets()
        configureActions()

        val savedKey =
            preferences()
                .getString(
                    KEY_SUBSCRIPTION,
                    ""
                )
                .orEmpty()

        if (savedKey.isBlank()) {
            showKeyEntry()
        } else {
            activeKey = savedKey
            fetchSubscription(
                savedKey,
                saveOnSuccess = false
            )
        }
    }

    override fun onResume() {
        super.onResume()

        AppTypography.apply(
            this,
            findViewById<View>(
                R.id.vipProfileRoot
            )
        )
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        keyCard =
            findViewById(
                R.id.vipKeyCard
            )

        loadingView =
            findViewById(
                R.id.vipLoadingView
            )

        errorCard =
            findViewById(
                R.id.vipErrorCard
            )

        errorText =
            findViewById(
                R.id.vipErrorText
            )

        dashboardCard =
            findViewById(
                R.id.vipDashboardCard
            )

        planTitle =
            findViewById(
                R.id.vipPlanTitle
            )

        planSubtitle =
            findViewById(
                R.id.vipPlanSubtitle
            )

        statusText =
            findViewById(
                R.id.vipStatusText
            )

        expiryText =
            findViewById(
                R.id.vipExpiryText
            )

        usageProgress =
            findViewById(
                R.id.vipUsageProgress
            )

        usedTraffic =
            findViewById(
                R.id.vipUsedTraffic
            )

        totalTraffic =
            findViewById(
                R.id.vipTotalTraffic
            )

        remainingTraffic =
            findViewById(
                R.id.vipRemainingTraffic
            )

        bannersTitle =
            findViewById(
                R.id.vipBannersTitle
            )

        bannersContainer =
            findViewById(
                R.id.vipBannersContainer
            )

        keyInput =
            findViewById(
                R.id.vipKeyInput
            )

        keyInput.inputType =
            InputType.TYPE_CLASS_TEXT or
                InputType
                    .TYPE_TEXT_VARIATION_PASSWORD or
                InputType
                    .TYPE_TEXT_FLAG_NO_SUGGESTIONS

        openButton =
            findViewById(
                R.id.vipOpenButton
            )

        retryButton =
            findViewById(
                R.id.vipRetryButton
            )

        refreshButton =
            findViewById(
                R.id.vipRefreshButton
            )

        syncButton =
            findViewById(
                R.id.vipSyncButton
            )

        removeButton =
            findViewById(
                R.id.vipRemoveButton
            )
    }

    private fun configureInsets() {
        val root =
            findViewById<View>(
                R.id.vipProfileRoot
            )

        ViewCompat
            .setOnApplyWindowInsetsListener(
                root
            ) {
                    view,
                    insets ->
                val bars =
                    insets.getInsets(
                        WindowInsetsCompat
                            .Type.systemBars()
                    )

                view.updatePadding(
                    top = bars.top,
                    bottom = bars.bottom
                )

                insets
            }
    }

    private fun configureActions() {
        findViewById<View>(
            R.id.vipBackButton
        ).setOnClickListener {
            finish()
        }

        openButton.setOnClickListener {
            val entered =
                keyInput.text
                    ?.toString()
                    .orEmpty()

            val validated =
                runCatching {
                    api.validateKey(
                        entered
                    )
                }.getOrElse {
                    keyInput.error =
                        getString(
                            R.string
                                .vip_key_invalid
                        )
                    return@setOnClickListener
                }

            keyInput.error = null

            fetchSubscription(
                validated,
                saveOnSuccess = true
            )
        }

        retryButton.setOnClickListener {
            val key =
                activeKey.ifBlank {
                    keyInput.text
                        ?.toString()
                        .orEmpty()
                }

            if (key.isNotBlank()) {
                fetchSubscription(
                    key,
                    saveOnSuccess =
                        activeKey.isBlank()
                )
            } else {
                showKeyEntry()
            }
        }

        refreshButton.setOnClickListener {
            if (activeKey.isNotBlank()) {
                fetchSubscription(
                    activeKey,
                    saveOnSuccess = false
                )
            }
        }

        syncButton.setOnClickListener {
            val response =
                currentResponse
                    ?: return@setOnClickListener

            syncPremiumRoutes(
                response,
                showResult = true
            )
        }

        removeButton.setOnClickListener {
            confirmRemoveSubscription()
        }
    }

    private fun fetchSubscription(
        key: String,
        saveOnSuccess: Boolean
    ) {
        showLoading()

        worker.execute {
            val result =
                runCatching {
                    val response =
                        api.fetch(key)

                    val synced =
                        syncResponseRoutes(
                            response
                        )

                    response to synced
                }

            mainHandler.post {
                if (
                    isFinishing ||
                    isDestroyed
                ) {
                    return@post
                }

                result.fold(
                    onSuccess = {
                            pair ->
                        val response =
                            pair.first

                        activeKey = key
                        currentResponse =
                            response

                        if (saveOnSuccess) {
                            preferences()
                                .edit()
                                .putString(
                                    KEY_SUBSCRIPTION,
                                    key
                                )
                                .apply()
                        }

                        renderDashboard(
                            response
                        )
                    },
                    onFailure = {
                            error ->
                        showError(
                            error.message
                                ?: getString(
                                    R.string
                                        .vip_load_failed
                                )
                        )
                    }
                )
            }
        }
    }

    private fun syncPremiumRoutes(
        response: VipSubscriptionResponse,
        showResult: Boolean
    ) {
        setButtonsEnabled(
            false
        )

        worker.execute {
            val result =
                runCatching {
                    syncResponseRoutes(
                        response
                    )
                }

            mainHandler.post {
                if (
                    isFinishing ||
                    isDestroyed
                ) {
                    return@post
                }

                setButtonsEnabled(
                    true
                )

                if (showResult) {
                    val message =
                        if (
                            result.getOrDefault(
                                false
                            )
                        ) {
                            getString(
                                R.string
                                    .vip_routes_ready
                            )
                        } else {
                            getString(
                                R.string
                                    .vip_no_supported_routes
                            )
                        }

                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun syncResponseRoutes(
        response: VipSubscriptionResponse
    ): Boolean {
        val now =
            System.currentTimeMillis()

        val servers =
            response.configs
                .mapNotNull {
                        item ->
                    val profile =
                        runCatching {
                            ShareLinkParser
                                .parse(
                                    item.config
                                )
                        }.getOrNull()
                            ?: return@mapNotNull null

                    SavedServer(
                        id =
                            UUID.randomUUID()
                                .toString(),
                        name =
                            item.name.ifBlank {
                                profile.name
                            },
                        rawLink =
                            item.config,
                        protocol =
                            protocolName(
                                profile
                            ),
                        address =
                            profile.address,
                        port =
                            profile.port,
                        transport =
                            profile.transport
                                .network
                                .uppercase(
                                    Locale.US
                                ),
                        createdAt = now,
                        origin =
                            ServerOrigin
                                .VIP_SUBSCRIPTION,
                        lastTestedAt = now
                    )
                }

        val merged =
            serverStore
                .mergeVipServers(
                    servers
                )

        if (merged) {
            VipRouteOptimizer.start(
                applicationContext
            )
        }

        return merged
    }

    private fun protocolName(
        profile: ProxyProfile
    ): String {
        return when (profile) {
            is ProxyProfile.Vmess ->
                "VMess"

            is ProxyProfile.Vless ->
                "VLESS"

            is ProxyProfile.Trojan ->
                "Trojan"

            is ProxyProfile.Shadowsocks ->
                "Shadowsocks"
        }
    }

    private fun renderDashboard(
        response: VipSubscriptionResponse
    ) {
        keyCard.visibility =
            View.GONE

        loadingView.visibility =
            View.GONE

        errorCard.visibility =
            View.GONE

        dashboardCard.visibility =
            View.VISIBLE

        planTitle.text =
            response.dashboard
                .title
                .cleanBannerValue()
                ?: response.subscription
                    .plan

        planSubtitle.text =
            response.dashboard
                .subtitle
                .cleanBannerValue()
                ?: response.subscription
                    .plan

        statusText.text =
            response.dashboard
                .status
                .cleanBannerValue()
                ?: "—"

        expiryText.text =
            response.dashboard
                .expireText
                .cleanBannerValue()
                ?: "—"

        usageProgress.max = 100

        val targetProgress =
            response.dashboard
                .progress
                .coerceIn(
                    0,
                    100
                )

        val indicatorColor =
            runCatching {
                Color.parseColor(
                    response.dashboard
                        .progressColor
                )
            }.getOrDefault(
                ContextCompat.getColor(
                    this,
                    R.color
                        .vico_premium_orange
                )
            )

        usageProgress.setIndicatorColor(
            indicatorColor
        )

        val traffic =
            response.subscription
                .traffic

        usedTraffic.text =
            getString(
                R.string.vip_used_value,
                traffic.usedGb
            )

        totalTraffic.text =
            getString(
                R.string.vip_total_value,
                traffic.totalGb
            )

        remainingTraffic.text =
            getString(
                R.string.vip_remaining_value,
                traffic.remainingGb
            )

        setButtonsEnabled(
            true
        )

        syncButton.isEnabled =
            response.configs
                .isNotEmpty() &&
                !response.subscription
                    .expiry
                    .expired

        renderBanners(
            response.banners
        )

        AppTypography.apply(
            this,
            findViewById<View>(
                R.id.vipProfileRoot
            )
        )

        animateDashboardEntrance(
            targetProgress
        )
    }

    private fun renderBanners(
        banners: List<VipBanner>
    ) {
        bannersContainer.removeAllViews()

        val dismissed =
            preferences()
                .getStringSet(
                    KEY_DISMISSED,
                    emptySet()
                )
                ?.toSet()
                ?: emptySet()

        val visible =
            banners.filterNot {
                it.id in dismissed
            }

        bannersTitle.visibility =
            if (visible.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        bannersContainer.visibility =
            if (visible.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        visible.forEach {
                banner ->
            bannersContainer.addView(
                createBannerCard(
                    banner
                )
            )
        }
    }

    private fun createBannerCard(
        banner: VipBanner
    ): MaterialCardView {
        val titleText =
            banner.title
                .cleanBannerValue()
                ?: getString(
                    R.string
                        .vip_announcement_default_title
                )

        val messageText =
            banner.message
                .cleanBannerValue()

        val actionText =
            banner.buttonText
                .cleanBannerValue()

        val actionUrl =
            banner.buttonUrl
                .cleanBannerValue()

        val card =
            MaterialCardView(this).apply {
                radius =
                    24.dp.toFloat()

                cardElevation =
                    0f

                strokeWidth =
                    1.dp

                strokeColor =
                    ContextCompat.getColor(
                        this@VipProfileActivity,
                        R.color
                            .vico_premium_outline
                    )

                setCardBackgroundColor(
                    ContextCompat.getColor(
                        this@VipProfileActivity,
                        R.color
                            .vico_premium_card
                    )
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout
                            .LayoutParams
                            .MATCH_PARENT,
                        LinearLayout
                            .LayoutParams
                            .WRAP_CONTENT
                    ).apply {
                        topMargin =
                            10.dp
                    }

                alpha =
                    0f

                translationY =
                    10.dp.toFloat()
            }

        val content =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    18.dp,
                    16.dp,
                    18.dp,
                    16.dp
                )
            }

        val header =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        header.addView(
            TextView(this).apply {
                text =
                    titleText

                textSize =
                    16f

                setTextColor(
                    ContextCompat.getColor(
                        this@VipProfileActivity,
                        R.color
                            .vico_premium_white
                    )
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout
                            .LayoutParams
                            .WRAP_CONTENT,
                        1f
                    )
            }
        )

        if (banner.dismissible) {
            header.addView(
                MaterialButton(this).apply {
                    text = "×"
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(
                        0,
                        0,
                        0,
                        0
                    )
                    cornerRadius = 18.dp

                    backgroundTintList =
                        ColorStateList.valueOf(
                            Color.TRANSPARENT
                        )

                    setTextColor(
                        ContextCompat.getColor(
                            this@VipProfileActivity,
                            R.color
                                .vico_premium_muted
                        )
                    )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            40.dp,
                            40.dp
                        )

                    setOnClickListener {
                        dismissBanner(
                            banner.id
                        )
                    }
                }
            )
        }

        content.addView(
            header
        )

        if (messageText != null) {
            content.addView(
                TextView(this).apply {
                    text =
                        messageText

                    textSize =
                        14f

                    setTextColor(
                        ContextCompat.getColor(
                            this@VipProfileActivity,
                            R.color
                                .vico_premium_muted
                        )
                    )

                    setPadding(
                        0,
                        8.dp,
                        0,
                        0
                    )
                }
            )
        }

        if (
            actionText != null &&
            actionUrl != null
        ) {
            content.addView(
                MaterialButton(this).apply {
                    text =
                        actionText

                    isAllCaps =
                        false

                    cornerRadius =
                        20.dp

                    backgroundTintList =
                        ColorStateList.valueOf(
                            Color.TRANSPARENT
                        )

                    strokeWidth =
                        1.dp

                    strokeColor =
                        ColorStateList.valueOf(
                            ContextCompat.getColor(
                                this@VipProfileActivity,
                                R.color
                                    .vico_premium_orange
                            )
                        )

                    setTextColor(
                        ContextCompat.getColor(
                            this@VipProfileActivity,
                            R.color
                                .vico_premium_orange
                        )
                    )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout
                                .LayoutParams
                                .WRAP_CONTENT,
                            48.dp
                        ).apply {
                            topMargin =
                                12.dp
                        }

                    setOnClickListener {
                        openSafeUrl(
                            actionUrl
                        )
                    }
                }
            )
        }

        card.addView(
            content
        )

        AppTypography.apply(
            this,
            card
        )

        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(
                DecelerateInterpolator()
            )
            .start()

        return card
    }

    private fun dismissBanner(
        id: String
    ) {
        val current =
            preferences()
                .getStringSet(
                    KEY_DISMISSED,
                    emptySet()
                )
                ?.toMutableSet()
                ?: mutableSetOf()

        current.add(id)

        preferences()
            .edit()
            .putStringSet(
                KEY_DISMISSED,
                current
            )
            .apply()

        currentResponse?.let {
            renderBanners(
                it.banners
            )
        }
    }

    private fun openSafeUrl(
        raw: String
    ) {
        val uri =
            runCatching {
                Uri.parse(raw)
            }.getOrNull()
                ?: return

        val scheme =
            uri.scheme
                ?.lowercase()
                ?: return

        if (
            scheme !in
                setOf(
                    "https",
                    "http",
                    "tg",
                    "mailto"
                )
        ) {
            return
        }

        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )
        }
    }

    private fun confirmRemoveSubscription() {
        val storedKey =
            preferences()
                .getString(
                    KEY_SUBSCRIPTION,
                    ""
                )
                .orEmpty()

        if (
            activeKey.isBlank() &&
            storedKey.isBlank()
        ) {
            showKeyEntry()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(
                R.string.vip_remove_title
            )
            .setMessage(
                R.string.vip_remove_message
            )
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .setPositiveButton(
                R.string.vip_remove_confirm
            ) {
                    _,
                    _ ->
                removeButton.isEnabled =
                    false

                val preferencesCleared =
                    preferences()
                        .edit()
                        .remove(
                            KEY_SUBSCRIPTION
                        )
                        .remove(
                            KEY_DISMISSED
                        )
                        .commit()

                val routesCleared =
                    runCatching {
                        serverStore
                            .removeVipServers()
                    }.isSuccess

                activeKey = ""
                currentResponse = null

                keyInput.setText("")

                bannersContainer
                    .removeAllViews()

                bannersTitle.visibility =
                    View.GONE

                setButtonsEnabled(
                    true
                )

                showKeyEntry()

                AppTypography.apply(
                    this,
                    findViewById<View>(
                        R.id.vipProfileRoot
                    )
                )

                setResult(
                    RESULT_OK,
                    Intent().putExtra(
                        "vip_subscription_removed",
                        true
                    )
                )

                Toast.makeText(
                    this,
                    getString(
                        if (
                            preferencesCleared &&
                            routesCleared
                        ) {
                            R.string.vip_removed
                        } else {
                            R.string.vip_remove_failed
                        }
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun showKeyEntry() {
        loadingView.visibility =
            View.GONE

        errorCard.visibility =
            View.GONE

        dashboardCard.visibility =
            View.GONE

        bannersTitle.visibility =
            View.GONE

        bannersContainer.visibility =
            View.GONE

        keyCard.visibility =
            View.VISIBLE
    }

    private fun showLoading() {
        keyCard.visibility =
            View.GONE

        errorCard.visibility =
            View.GONE

        dashboardCard.visibility =
            View.GONE

        bannersTitle.visibility =
            View.GONE

        bannersContainer.visibility =
            View.GONE

        loadingView.visibility =
            View.VISIBLE

        setButtonsEnabled(
            false
        )
    }

    private fun showError(
        message: String
    ) {
        loadingView.visibility =
            View.GONE

        dashboardCard.visibility =
            View.GONE

        bannersTitle.visibility =
            View.GONE

        bannersContainer.visibility =
            View.GONE

        errorText.text =
            message

        errorCard.visibility =
            View.VISIBLE

        keyCard.visibility =
            if (activeKey.isBlank()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        setButtonsEnabled(
            true
        )
    }

    private fun setButtonsEnabled(
        enabled: Boolean
    ) {
        openButton.isEnabled =
            enabled

        retryButton.isEnabled =
            enabled

        refreshButton.isEnabled =
            enabled

        syncButton.isEnabled =
            enabled &&
                currentResponse
                    ?.configs
                    ?.isNotEmpty() ==
                true

        removeButton.isEnabled =
            enabled
    }

    private fun String?.cleanBannerValue():
        String? {
        val value =
            this
                ?.trim()
                .orEmpty()

        return value.takeIf {
            it.isNotBlank() &&
                !it.equals(
                    "null",
                    ignoreCase = true
                ) &&
                !it.equals(
                    "undefined",
                    ignoreCase = true
                ) &&
                it != "—"
        }
    }

    private fun animateDashboardEntrance(
        targetProgress: Int
    ) {
        val orderedViews =
            listOf(
                R.id.vipPlanCard,
                R.id.vipStatusPill,
                R.id.vipExpiryPill,
                R.id.vipUsageCard,
                R.id.vipUsedPill,
                R.id.vipTotalPill,
                R.id.vipRemainingPill,
                R.id.vipSyncButton,
                R.id.vipRemoveButton
            ).mapNotNull {
                    id ->
                findViewById<View>(
                    id
                )
            }

        orderedViews.forEachIndexed {
                index,
                view ->
            view.animate()
                .cancel()

            view.alpha =
                0f

            view.translationY =
                12.dp.toFloat()

            view.scaleX =
                0.985f

            view.scaleY =
                0.985f

            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(
                    index * 34L
                )
                .setDuration(
                    230L
                )
                .setInterpolator(
                    DecelerateInterpolator()
                )
                .start()
        }

        ObjectAnimator.ofInt(
            usageProgress,
            "progress",
            usageProgress.progress,
            targetProgress
        ).apply {
            duration =
                520L

            interpolator =
                DecelerateInterpolator()

            start()
        }

        val statusPill =
            findViewById<View>(
                R.id.vipStatusPill
            )

        statusPill
            .animate()
            .scaleX(1.025f)
            .scaleY(1.025f)
            .setStartDelay(280L)
            .setDuration(120L)
            .withEndAction {
                statusPill
                    .animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .start()
            }
            .start()
    }

    private fun preferences() =
        getSharedPreferences(
            PREFERENCES,
            MODE_PRIVATE
        )

    private val Int.dp: Int
        get() =
            (
                this *
                    resources
                        .displayMetrics
                        .density
                ).toInt()
}
