package com.vicovpn.client.profile

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.card.MaterialCardView
import com.vicovpn.client.R
import kotlin.math.max
import kotlin.math.min

object HomeUiPolishController {

    fun install(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        applyFonts(activity, root)
        sanitizeAllTexts(root)
        compactHomeLayout(activity, root)
        styleMetricCards(activity, root)
        styleVipAndLocationCards(activity, root)
        stripOrbShadow(activity, root)
        hideHomeBannersByDefault(activity, root)
        applyInsets(activity, root)
    }

    private fun applyInsets(activity: Activity, root: ViewGroup) {
        val scroll = findScroll(root)
        val bottomNav = findOptionalView(
            activity,
            "bottomNavBar",
            "bottomNavigationBar",
            "bottomNavigation",
            "bottomNav",
            "navigationContainer"
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navHeight = bottomNav?.height?.takeIf { it > 0 } ?: dp(activity, 92)
            val extraBottom = navHeight + dp(activity, 20) + bars.bottom

            scroll?.let {
                it.clipToPadding = false
                it.updatePadding(bottom = extraBottom)
            }

            bottomNav?.updatePadding(
                left = bottomNav.paddingLeft,
                top = bottomNav.paddingTop,
                right = bottomNav.paddingRight,
                bottom = max(dp(activity, 10), bars.bottom)
            )

            insets
        }

        root.requestApplyInsets()
    }

    private fun compactHomeLayout(activity: Activity, root: ViewGroup) {
        val orb = findOptionalView(
            activity,
            "connectionOrbView",
            "connectOrbView",
            "connectButton",
            "mainConnectButton",
            "centerConnectButton"
        )

        orb?.let { view ->
            val targetSize = min(dp(activity, 250), (activity.resources.displayMetrics.widthPixels * 0.52f).toInt())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = targetSize
                height = targetSize
                topMargin = dp(activity, 14)
                bottomMargin = dp(activity, 14)
            }
        }

        val statusCard = findOptionalView(
            activity,
            "selectedServerCard",
            "selectedLocationCard",
            "locationCard",
            "statusLocationCard"
        )
        statusCard?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = dp(activity, 12)
            bottomMargin = dp(activity, 8)
        }

        val priorityCard = findOptionalView(
            activity,
            "connectionPriorityCard",
            "priorityCard",
            "homePriorityCard"
        )
        priorityCard?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = dp(activity, 8)
            bottomMargin = dp(activity, 8)
        }

        val uploadCard = findOptionalView(activity, "uploadCard", "cardUpload")
        val downloadCard = findOptionalView(activity, "downloadCard", "cardDownload")

        listOfNotNull(uploadCard, downloadCard).forEach { card ->
            card.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = dp(activity, 8)
            }
        }
    }

    private fun styleMetricCards(activity: Activity, root: ViewGroup) {
        val dark = isDark(activity)

        val cardBg = if (dark) {
            ContextCompat.getColor(activity, android.R.color.transparent)
        } else {
            0xFFF7F7FA.toInt()
        }

        val circleBg = if (dark) {
            0xFF34343C.toInt()
        } else {
            0xFF31313A.toInt()
        }

        val iconTint = if (dark) {
            0xFFFF6B3D.toInt()
        } else {
            0xFFFF6B3D.toInt()
        }

        val metricCards = listOfNotNull(
            findOptionalView(activity, "uploadCard", "cardUpload"),
            findOptionalView(activity, "downloadCard", "cardDownload")
        )

        metricCards.forEach { card ->
            styleRoundedCard(card, activity)
            if (card is MaterialCardView) {
                card.setCardBackgroundColor(cardBg)
            }
            if (card is CardView) {
                card.setCardBackgroundColor(cardBg)
            }
            tintChildImageViews(card, circleBg, iconTint)
        }
    }

    private fun styleVipAndLocationCards(activity: Activity, root: ViewGroup) {
        val targets = listOfNotNull(
            findOptionalView(activity, "selectedServerCard", "selectedLocationCard", "locationCard", "statusLocationCard"),
            findOptionalView(activity, "connectionPriorityCard", "priorityCard", "homePriorityCard"),
            findOptionalView(activity, "vipSummaryCard", "vipSectionCard", "vipHomeCard"),
            findOptionalView(activity, "vipHomeSectionContainer", "vipSectionContainer")
        )

        targets.forEach { view ->
            styleRoundedCard(view, activity)
        }
    }

    private fun stripOrbShadow(activity: Activity, root: ViewGroup) {
        val orb = findOptionalView(
            activity,
            "connectionOrbView",
            "connectOrbView",
            "connectButton",
            "mainConnectButton",
            "centerConnectButton"
        ) ?: return

        orb.elevation = 0f
        orb.translationZ = 0f
        orb.stateListAnimator = null

        val parent1 = orb.parent as? View
        val parent2 = parent1?.parent as? View

        listOfNotNull(parent1, parent2).forEach { ancestor ->
            ancestor.elevation = 0f
            ancestor.translationZ = 0f
            ancestor.stateListAnimator = null

            val name = safeName(activity, ancestor.id)
            if (
                name.contains("orb", true) ||
                name.contains("connect", true) ||
                name.contains("button", true)
            ) {
                ancestor.background = null
            }
        }
    }

    private fun hideHomeBannersByDefault(activity: Activity, root: ViewGroup) {
        if (HomeBannerPrefs.isEnabled(activity)) return

        val container = findOptionalView(
            activity,
            "homeBannerContainer",
            "bannerContainer",
            "publicBannerContainer",
            "topBannerContainer"
        ) ?: return

        container.visibility = View.GONE
    }

    private fun tintChildImageViews(root: View, circleBg: Int, iconTint: Int) {
        if (root is ImageView) {
            root.setColorFilter(iconTint)
            val parent = root.parent as? View
            if (parent is MaterialCardView) {
                parent.setCardBackgroundColor(circleBg)
                parent.radius = parent.resources.displayMetrics.density * 28f
            }
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                tintChildImageViews(root.getChildAt(i), circleBg, iconTint)
            }
        }
    }

    private fun styleRoundedCard(view: View, context: Context) {
        when (view) {
            is MaterialCardView -> {
                view.radius = dp(context, 18).toFloat()
                view.cardElevation = 0f
                view.strokeWidth = dp(context, 1)
                view.strokeColor = if (isDark(context)) 0xFF4A4A53.toInt() else 0xFFD9D9E2.toInt()
            }
            is CardView -> {
                view.radius = dp(context, 18).toFloat()
                view.cardElevation = 0f
            }
        }
    }

    private fun applyFonts(context: Context, view: View) {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }

        val isPersian = locale != null && locale.language.startsWith("fa")

        val header = ResourcesCompat.getFont(
            context,
            if (isPersian) R.font.vico_header_titles_persian else R.font.vico_header_titles_english
        )
        val body = ResourcesCompat.getFont(
            context,
            if (isPersian) R.font.vico_text_persian else R.font.vico_text_english
        )

        applyFontsRecursive(view, header, body)
    }

    private fun applyFontsRecursive(view: View, header: Typeface?, body: Typeface?) {
        if (view is TextView) {
            val sizeSp = view.textSize / view.resources.displayMetrics.scaledDensity
            view.typeface = if (sizeSp >= 20f || view.maxLines == 1 && sizeSp >= 16f) header ?: view.typeface else body ?: view.typeface
            view.includeFontPadding = false
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFontsRecursive(view.getChildAt(i), header, body)
            }
        }
    }

    private fun sanitizeAllTexts(root: View) {
        walk(root) { view ->
            if (view is TextView) {
                val raw = view.text?.toString() ?: return@walk
                val fixed = sanitizeText(raw)
                if (fixed != raw) {
                    view.text = fixed
                }
            }
        }
    }

    private fun sanitizeText(input: String): String {
        return input
            .replace("Â·", " • ")
            .replace("Â ", " ")
            .replace("Â", "")
            .replace("â€¢", "•")
            .replace("Ã—", "×")
            .replace("Ã", "")
            .replace("  ", " ")
            .trim()
    }

    private fun walk(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walk(view.getChildAt(i), action)
            }
        }
    }

    private fun findScroll(root: View): ViewGroup? {
        if (root is NestedScrollView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findScroll(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun findOptionalView(activity: Activity, vararg names: String): View? {
        for (name in names) {
            val id = activity.resources.getIdentifier(name, "id", activity.packageName)
            if (id != 0) {
                val view = activity.findViewById<View>(id)
                if (view != null) return view
            }
        }
        return null
    }

    private fun safeName(context: Context, id: Int): String {
        return try {
            if (id == View.NO_ID) "" else context.resources.getResourceEntryName(id)
        } catch (_: Exception) {
            ""
        }
    }

    private fun isDark(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
