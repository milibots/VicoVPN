package com.vicovpn.client.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import com.vicovpn.client.R
import java.util.Locale

object AppTypography {

    private var cachedLanguage: String? = null
    private var cachedHeader: Typeface? = null
    private var cachedBody: Typeface? = null

    fun apply(
        context: Context,
        root: View
    ) {
        val language =
            context.resources.configuration
                .locales
                .get(0)
                .language
                .lowercase(Locale.ROOT)

        val typefaces =
            resolveTypefaces(
                context,
                language
            )

        applyRecursively(
            context = context,
            view = root,
            header = typefaces.first,
            body = typefaces.second
        )
    }

    private fun resolveTypefaces(
        context: Context,
        language: String
    ): Pair<Typeface?, Typeface?> {
        if (
            cachedLanguage == language &&
            cachedHeader != null &&
            cachedBody != null
        ) {
            return cachedHeader to cachedBody
        }

        val persian =
            language == "fa"

        cachedLanguage = language
        cachedHeader =
            ResourcesCompat.getFont(
                context,
                if (persian) {
                    R.font.vico_header_titles_persian
                } else {
                    R.font.vico_header_titles_english
                }
            )

        cachedBody =
            ResourcesCompat.getFont(
                context,
                if (persian) {
                    R.font.vico_text_persian
                } else {
                    R.font.vico_text_english
                }
            )

        return cachedHeader to cachedBody
    }

    private fun applyRecursively(
        context: Context,
        view: View,
        header: Typeface?,
        body: Typeface?
    ) {
        if (view is TextView) {
            val sizeSp =
                view.textSize /
                    context.resources
                        .displayMetrics
                        .scaledDensity

            val resourceName =
                if (view.id != View.NO_ID) {
                    runCatching {
                        context.resources
                            .getResourceEntryName(
                                view.id
                            )
                    }.getOrDefault("")
                } else {
                    ""
                }

            val looksLikeHeading =
                sizeSp >= 19f ||
                    resourceName.contains(
                        "title",
                        ignoreCase = true
                    ) ||
                    resourceName.contains(
                        "statusText",
                        ignoreCase = true
                    ) ||
                    resourceName.contains(
                        "serverName",
                        ignoreCase = true
                    )

            val preferred =
                if (
                    looksLikeHeading &&
                    view !is MaterialButton
                ) {
                    header ?: body
                } else {
                    body ?: header
                }

            val originalStyle =
                view.typeface?.style
                    ?: Typeface.NORMAL

            view.typeface =
                preferred?.let {
                    Typeface.create(
                        it,
                        originalStyle
                    )
                }

            view.includeFontPadding = false
            view.fontFeatureSettings = "kern"
        }

        if (view is ViewGroup) {
            for (
                index in 0 until
                    view.childCount
            ) {
                applyRecursively(
                    context,
                    view.getChildAt(index),
                    header,
                    body
                )
            }
        }
    }
}
