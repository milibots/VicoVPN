package com.vicovpn.client.profile

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.vicovpn.client.R
import com.vicovpn.client.ui.AppTypography
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class HomeBannerController(
    private val activity: AppCompatActivity,
    private val container: LinearLayout
) {
    companion object {
        private const val PREFERENCES =
            "home_public_banners"
        private const val KEY_ENABLED =
            "notification_center_enabled"
        private const val KEY_CACHE =
            "cache"
        private const val KEY_CACHE_AT =
            "cache_at"
        private const val KEY_DISMISSED =
            "dismissed"
        private const val CACHE_MS =
            60_000L
    }

    private val executor =
        Executors.newSingleThreadExecutor()

    private val loading =
        AtomicBoolean(false)

    private var destroyed =
        false

    private var current =
        emptyList<VipBanner>()

    private val notificationCard:
        MaterialCardView? =
        activity.findViewById(
            R.id.announcementBellCard
        )

    private val notificationBadge:
        TextView? =
        activity.findViewById(
            R.id.announcementBadge
        )

    private val settingsButton:
        View? =
        activity.findViewById(
            R.id.settingsAnnouncementButton
        )

    private val settingsValue:
        TextView? =
        activity.findViewById(
            R.id.settingsAnnouncementValue
        )

    init {
        container.removeAllViews()
        container.visibility =
            View.GONE

        notificationCard
            ?.setOnClickListener {
                showAnnouncementsSheet()
            }

        settingsButton
            ?.setOnClickListener {
                showVisibilitySheet()
            }

        updateSettingsUi()
        renderNotificationIcon()
    }

    fun refresh(
        force: Boolean = false
    ) {
        if (destroyed) {
            return
        }

        container.removeAllViews()
        container.visibility =
            View.GONE

        updateSettingsUi()

        if (!isEnabled()) {
            current =
                emptyList()

            renderNotificationIcon()
            return
        }

        val preferences =
            preferences()

        val cached =
            decode(
                preferences.getString(
                    KEY_CACHE,
                    ""
                ).orEmpty()
            )

        if (cached.isNotEmpty()) {
            current = cached
            renderNotificationIcon()
        }

        val age =
            System.currentTimeMillis() -
                preferences.getLong(
                    KEY_CACHE_AT,
                    0L
                )

        if (
            !force &&
            age in 0 until CACHE_MS
        ) {
            return
        }

        if (
            !loading.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        executor.execute {
            val result =
                runCatching {
                    PublicBannerClient()
                        .fetch()
                }

            activity.runOnUiThread {
                loading.set(false)

                if (
                    destroyed ||
                    activity.isFinishing ||
                    activity.isDestroyed
                ) {
                    return@runOnUiThread
                }

                result.onSuccess {
                        banners ->
                    current = banners

                    preferences()
                        .edit()
                        .putString(
                            KEY_CACHE,
                            encode(
                                banners
                            )
                        )
                        .putLong(
                            KEY_CACHE_AT,
                            System.currentTimeMillis()
                        )
                        .apply()

                    renderNotificationIcon()
                }
            }
        }
    }

    fun destroy() {
        destroyed = true
        executor.shutdownNow()
    }

    private fun isEnabled():
        Boolean {
        return preferences()
            .getBoolean(
                KEY_ENABLED,
                false
            )
    }

    private fun setEnabled(
        enabled: Boolean
    ) {
        preferences()
            .edit()
            .putBoolean(
                KEY_ENABLED,
                enabled
            )
            .apply()

        updateSettingsUi()

        if (enabled) {
            refresh(
                force = true
            )
        } else {
            current =
                emptyList()

            notificationCard
                ?.visibility =
                View.GONE
        }
    }

    private fun updateSettingsUi() {
        settingsValue?.text =
            activity.getString(
                if (isEnabled()) {
                    R.string
                        .home_notifications_shown
                } else {
                    R.string
                        .home_notifications_hidden
                }
            )
    }

    private fun visibleBanners():
        List<VipBanner> {
        val dismissed =
            preferences()
                .getStringSet(
                    KEY_DISMISSED,
                    emptySet()
                )
                ?.toSet()
                ?: emptySet()

        return current.filter {
            it.id !in dismissed
        }
    }

    private fun renderNotificationIcon() {
        val visible =
            if (isEnabled()) {
                visibleBanners()
            } else {
                emptyList()
            }

        val card =
            notificationCard
                ?: return

        if (visible.isEmpty()) {
            card.visibility =
                View.GONE
            return
        }

        card.visibility =
            View.VISIBLE

        val count =
            visible.size

        notificationBadge?.apply {
            text =
                if (count > 99) {
                    "99+"
                } else {
                    count.toString()
                }

            visibility =
                View.VISIBLE
        }

        card.contentDescription =
            activity.getString(
                R.string
                    .home_notifications_count,
                count
            )

        card.alpha = 0f
        card.scaleX = 0.9f
        card.scaleY = 0.9f

        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180L)
            .start()
    }

    private fun showVisibilitySheet() {
        val dialog =
            BottomSheetDialog(
                activity
            )

        val root =
            createSheetRoot()

        root.addView(
            createSheetHandle()
        )

        root.addView(
            TextView(
                activity
            ).apply {
                text =
                    activity.getString(
                        R.string
                            .home_notifications_setting
                    )

                textSize = 21f

                setTextColor(
                    color(
                        R.color
                            .vico_premium_white
                    )
                )

                setPadding(
                    2.dp,
                    22.dp,
                    2.dp,
                    6.dp
                )
            }
        )

        root.addView(
            TextView(
                activity
            ).apply {
                text =
                    activity.getString(
                        R.string
                            .home_notifications_setting_description
                    )

                textSize = 13f

                setTextColor(
                    color(
                        R.color
                            .vico_premium_muted
                    )
                )

                setPadding(
                    2.dp,
                    0,
                    2.dp,
                    14.dp
                )
            }
        )

        val options =
            listOf(
                true to
                    R.string
                        .home_notifications_show,
                false to
                    R.string
                        .home_notifications_hide
            )

        options.forEach {
                (enabled, label) ->
            val selected =
                enabled ==
                    isEnabled()

            root.addView(
                MaterialButton(
                    activity
                ).apply {
                    text =
                        activity.getString(
                            label
                        )

                    isAllCaps = false
                    cornerRadius = 22.dp
                    strokeWidth = 1.dp

                    strokeColor =
                        ColorStateList.valueOf(
                            color(
                                if (selected) {
                                    R.color
                                        .vico_premium_orange
                                } else {
                                    R.color
                                        .vico_premium_outline
                                }
                            )
                        )

                    backgroundTintList =
                        ColorStateList.valueOf(
                            color(
                                if (selected) {
                                    R.color
                                        .vico_premium_selected_surface
                                } else {
                                    R.color
                                        .vico_premium_card_alt
                                }
                            )
                        )

                    setTextColor(
                        color(
                            R.color
                                .vico_premium_white
                        )
                    )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams
                                .MATCH_PARENT,
                            56.dp
                        ).apply {
                            topMargin =
                                8.dp
                        }

                    setOnClickListener {
                        setEnabled(
                            enabled
                        )

                        dialog.dismiss()
                    }
                }
            )
        }

        dialog.setContentView(
            root
        )

        styleSheet(
            dialog
        )

        dialog.show()
    }

    private fun showAnnouncementsSheet() {
        val banners =
            visibleBanners()

        if (banners.isEmpty()) {
            renderNotificationIcon()
            return
        }

        val dialog =
            BottomSheetDialog(
                activity
            )

        val root =
            createSheetRoot()

        root.addView(
            createSheetHandle()
        )

        val heading =
            LinearLayout(
                activity
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    2.dp,
                    20.dp,
                    2.dp,
                    10.dp
                )
            }

        heading.addView(
            TextView(
                activity
            ).apply {
                text =
                    activity.getString(
                        R.string
                            .home_notifications_title
                    )

                textSize = 22f

                setTextColor(
                    color(
                        R.color
                            .vico_premium_white
                    )
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams
                            .WRAP_CONTENT,
                        1f
                    )
            }
        )

        heading.addView(
            TextView(
                activity
            ).apply {
                text =
                    banners.size
                        .toString()

                textSize = 13f
                gravity =
                    Gravity.CENTER

                setTextColor(
                    color(
                        R.color
                            .vico_premium_white
                    )
                )

                setBackgroundResource(
                    R.drawable
                        .bg_premium_setting_icon
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        38.dp,
                        38.dp
                    )
            }
        )

        root.addView(
            heading
        )

        val scroll =
            NestedScrollView(
                activity
            ).apply {
                isFillViewport =
                    false

                overScrollMode =
                    View.OVER_SCROLL_NEVER

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                            .MATCH_PARENT,
                        (
                            activity.resources
                                .displayMetrics
                                .heightPixels *
                                0.52f
                            ).toInt()
                    )
            }

        val list =
            LinearLayout(
                activity
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    0,
                    0,
                    0,
                    8.dp
                )
            }

        banners.forEach {
                banner ->
            list.addView(
                createBannerCard(
                    banner,
                    dialog
                )
            )
        }

        scroll.addView(
            list
        )

        root.addView(
            scroll
        )

        root.addView(
            MaterialButton(
                activity
            ).apply {
                text =
                    activity.getString(
                        R.string
                            .home_notifications_close
                    )

                isAllCaps = false
                cornerRadius = 24.dp

                backgroundTintList =
                    ColorStateList.valueOf(
                        color(
                            R.color
                                .vico_premium_card_alt
                        )
                    )

                setTextColor(
                    color(
                        R.color
                            .vico_premium_white
                    )
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                            .MATCH_PARENT,
                        54.dp
                    ).apply {
                        topMargin =
                            10.dp
                    }

                setOnClickListener {
                    dialog.dismiss()
                }
            }
        )

        dialog.setContentView(
            root
        )

        styleSheet(
            dialog
        )

        dialog.show()

        AppTypography.apply(
            activity,
            root
        )
    }

    private fun createBannerCard(
        banner: VipBanner,
        dialog: BottomSheetDialog
    ): MaterialCardView {
        val card =
            MaterialCardView(
                activity
            ).apply {
                radius =
                    24.dp.toFloat()

                cardElevation =
                    0f

                strokeWidth =
                    1.dp

                strokeColor =
                    accent(
                        banner.type
                    )

                setCardBackgroundColor(
                    color(
                        R.color
                            .vico_premium_card
                    )
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                            .MATCH_PARENT,
                        LinearLayout.LayoutParams
                            .WRAP_CONTENT
                    ).apply {
                        topMargin =
                            9.dp
                    }
            }

        val content =
            LinearLayout(
                activity
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    16.dp,
                    15.dp,
                    16.dp,
                    15.dp
                )
            }

        val header =
            LinearLayout(
                activity
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        header.addView(
            TextView(
                activity
            ).apply {
                text =
                    symbol(
                        banner.type
                    )

                textSize = 17f

                gravity =
                    Gravity.CENTER

                setTextColor(
                    accent(
                        banner.type
                    )
                )

                setBackgroundResource(
                    R.drawable
                        .bg_premium_setting_icon
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        42.dp,
                        42.dp
                    )
            }
        )

        header.addView(
            TextView(
                activity
            ).apply {
                text =
                    banner.title

                textSize = 16f

                setTextColor(
                    color(
                        R.color
                            .vico_premium_white
                    )
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams
                            .WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart =
                            12.dp
                    }
            }
        )

        if (banner.dismissible) {
            header.addView(
                MaterialButton(
                    activity
                ).apply {
                    text = "×"
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    cornerRadius = 18.dp

                    backgroundTintList =
                        ColorStateList.valueOf(
                            Color.TRANSPARENT
                        )

                    setTextColor(
                        color(
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
                        dismiss(
                            banner.id
                        )

                        dialog.dismiss()

                        if (
                            visibleBanners()
                                .isNotEmpty()
                        ) {
                            showAnnouncementsSheet()
                        }
                    }
                }
            )
        }

        content.addView(
            header
        )

        if (
            banner.message
                .isNotBlank()
        ) {
            content.addView(
                TextView(
                    activity
                ).apply {
                    text =
                        banner.message

                    textSize = 14f

                    setTextColor(
                        color(
                            R.color
                                .vico_premium_muted
                        )
                    )

                    setPadding(
                        0,
                        10.dp,
                        0,
                        0
                    )
                }
            )
        }

        val buttonText =
            banner.buttonText
                ?.trim()
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.equals(
                            "null",
                            ignoreCase = true
                        )
                }

        val buttonUrl =
            banner.buttonUrl
                ?.trim()
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.equals(
                            "null",
                            ignoreCase = true
                        )
                }

        if (
            buttonText != null &&
            buttonUrl != null
        ) {
            content.addView(
                MaterialButton(
                    activity
                ).apply {
                    text =
                        buttonText

                    isAllCaps = false
                    cornerRadius = 20.dp
                    strokeWidth = 1.dp

                    strokeColor =
                        ColorStateList.valueOf(
                            accent(
                                banner.type
                            )
                        )

                    backgroundTintList =
                        ColorStateList.valueOf(
                            Color.TRANSPARENT
                        )

                    setTextColor(
                        accent(
                            banner.type
                        )
                    )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams
                                .WRAP_CONTENT,
                            48.dp
                        ).apply {
                            topMargin =
                                12.dp
                        }

                    setOnClickListener {
                        openUrl(
                            buttonUrl
                        )
                    }
                }
            )
        }

        card.addView(
            content
        )

        return card
    }

    private fun dismiss(
        id: String
    ) {
        val preferences =
            preferences()

        val dismissed =
            preferences
                .getStringSet(
                    KEY_DISMISSED,
                    emptySet()
                )
                ?.toMutableSet()
                ?: mutableSetOf()

        dismissed.add(id)

        preferences.edit()
            .putStringSet(
                KEY_DISMISSED,
                dismissed
            )
            .apply()

        renderNotificationIcon()
    }

    private fun openUrl(
        raw: String
    ) {
        val uri =
            runCatching {
                Uri.parse(
                    raw
                )
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
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )
        }
    }

    private fun createSheetRoot():
        LinearLayout {
        return LinearLayout(
            activity
        ).apply {
            orientation =
                LinearLayout.VERTICAL

            setPadding(
                20.dp,
                14.dp,
                20.dp,
                26.dp
            )

            setBackgroundResource(
                R.drawable
                    .bg_premium_sheet
            )
        }
    }

    private fun createSheetHandle():
        View {
        return View(
            activity
        ).apply {
            setBackgroundResource(
                R.drawable
                    .bg_premium_sheet_handle
            )

            layoutParams =
                LinearLayout.LayoutParams(
                    44.dp,
                    4.dp
                ).apply {
                    gravity =
                        Gravity.CENTER_HORIZONTAL
                }
        }
    }

    private fun styleSheet(
        dialog: BottomSheetDialog
    ) {
        dialog.setOnShowListener {
            dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id
                    .design_bottom_sheet
            )?.apply {
                setBackgroundColor(
                    Color.TRANSPARENT
                )

                AppTypography.apply(
                    activity,
                    this
                )
            }
        }
    }

    private fun symbol(
        type: String
    ): String {
        return when (
            type.lowercase()
        ) {
            "success" -> "✓"
            "warning" -> "!"
            "maintenance" -> "⚙"
            "error" -> "×"
            "promotion" -> "★"
            else -> "i"
        }
    }

    private fun accent(
        type: String
    ): Int {
        return when (
            type.lowercase()
        ) {
            "success" ->
                Color.rgb(
                    46,
                    204,
                    113
                )

            "warning",
            "maintenance" ->
                Color.rgb(
                    245,
                    158,
                    11
                )

            "error" ->
                Color.rgb(
                    239,
                    68,
                    68
                )

            else ->
                color(
                    R.color
                        .vico_premium_orange
                )
        }
    }

    private fun color(
        resource: Int
    ): Int {
        return ContextCompat.getColor(
            activity,
            resource
        )
    }

    private fun encode(
        banners: List<VipBanner>
    ): String {
        val array =
            JSONArray()

        banners.forEach {
                banner ->
            array.put(
                JSONObject().apply {
                    put(
                        "id",
                        banner.id
                    )
                    put(
                        "type",
                        banner.type
                    )
                    put(
                        "title",
                        banner.title
                    )
                    put(
                        "message",
                        banner.message
                    )
                    put(
                        "buttonText",
                        banner.buttonText
                    )
                    put(
                        "buttonUrl",
                        banner.buttonUrl
                    )
                    put(
                        "dismissible",
                        banner.dismissible
                    )
                    put(
                        "imageUrl",
                        banner.imageUrl
                    )
                    put(
                        "priority",
                        banner.priority
                    )
                }
            )
        }

        return array.toString()
    }

    private fun decode(
        value: String
    ): List<VipBanner> {
        if (value.isBlank()) {
            return emptyList()
        }

        return runCatching {
            val array =
                JSONArray(
                    value
                )

            val result =
                mutableListOf<VipBanner>()

            for (
                index in
                0 until array.length()
            ) {
                val item =
                    array.optJSONObject(
                        index
                    ) ?: continue

                val id =
                    item.optString(
                        "id",
                        ""
                    )

                val title =
                    item.optString(
                        "title",
                        ""
                    )

                if (
                    id.isBlank() ||
                    title.isBlank()
                ) {
                    continue
                }

                result.add(
                    VipBanner(
                        id = id,
                        type =
                            item.optString(
                                "type",
                                "info"
                            ),
                        title = title,
                        message =
                            item.optString(
                                "message",
                                ""
                            ),
                        buttonText =
                            nullableString(
                                item,
                                "buttonText"
                            ),
                        buttonUrl =
                            nullableString(
                                item,
                                "buttonUrl"
                            ),
                        dismissible =
                            item.optBoolean(
                                "dismissible",
                                true
                            ),
                        imageUrl =
                            nullableString(
                                item,
                                "imageUrl"
                            ),
                        priority =
                            item.optInt(
                                "priority",
                                0
                            )
                    )
                )
            }

            result
        }.getOrDefault(
            emptyList()
        )
    }

    private fun nullableString(
        source: JSONObject,
        name: String
    ): String? {
        if (
            source.isNull(
                name
            )
        ) {
            return null
        }

        return source
            .optString(
                name,
                ""
            )
            .trim()
            .takeIf {
                it.isNotBlank() &&
                    !it.equals(
                        "null",
                        ignoreCase = true
                    )
            }
    }

    private fun preferences() =
        activity.getSharedPreferences(
            PREFERENCES,
            Context.MODE_PRIVATE
        )

    private val Int.dp:
        Int
        get() =
            (
                this *
                    activity.resources
                        .displayMetrics
                        .density
                ).toInt()
}
