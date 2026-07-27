package com.vicovpn.client.profile

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.vicovpn.client.R
import com.vicovpn.client.server.SavedServer
import com.vicovpn.client.server.ServerOrigin
import com.vicovpn.client.server.ServerStore
import com.vicovpn.client.ui.AppTypography

object VipLocationSheet {

    fun show(
        activity: AppCompatActivity,
        store: ServerStore,
        onSelected: (
            SavedServer
        ) -> Unit
    ) {
        val dialog =
            BottomSheetDialog(
                activity
            )

        val root =
            LinearLayout(
                activity
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    20.dp(activity),
                    12.dp(activity),
                    20.dp(activity),
                    24.dp(activity)
                )

                setBackgroundColor(
                    ContextCompat.getColor(
                        activity,
                        R.color
                            .vico_premium_background
                    )
                )
            }

        root.addView(
            TextView(
                activity
            ).apply {
                text =
                    activity.getString(
                        R.string
                            .vip_locations_title
                    )
                textSize = 23f
                setTextColor(
                    ContextCompat.getColor(
                        activity,
                        R.color
                            .vico_premium_white
                    )
                )
                setPadding(
                    4.dp(activity),
                    8.dp(activity),
                    4.dp(activity),
                    4.dp(activity)
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
                            .vip_locations_description
                    )
                textSize = 14f
                setTextColor(
                    ContextCompat.getColor(
                        activity,
                        R.color
                            .vico_premium_muted
                    )
                )
                setPadding(
                    4.dp(activity),
                    0,
                    4.dp(activity),
                    14.dp(activity)
                )
            }
        )

        val scroll =
            ScrollView(
                activity
            ).apply {
                isFillViewport = true

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams
                            .MATCH_PARENT,
                        0,
                        1f
                    )
            }

        val list =
            LinearLayout(
                activity
            ).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        scroll.addView(
            list
        )

        root.addView(
            scroll
        )

        fun render() {
            if (
                !dialog.isShowing &&
                list.childCount > 0
            ) {
                return
            }

            list.removeAllViews()

            val routes =
                store.getServers()
                    .filter {
                        it.origin ==
                            ServerOrigin
                                .VIP_SUBSCRIPTION
                    }
                    .sortedWith(
                        compareBy<SavedServer> {
                            if (
                                it.latencyMs > 0
                            ) {
                                it.latencyMs
                            } else {
                                Long.MAX_VALUE
                            }
                        }.thenBy {
                            displayName(
                                it.name
                            )
                        }
                    )

            val activeId =
                store.getActiveServerId()

            if (routes.isEmpty()) {
                list.addView(
                    TextView(
                        activity
                    ).apply {
                        text =
                            activity.getString(
                                R.string
                                    .vip_no_locations
                            )
                        gravity =
                            Gravity.CENTER
                        textSize = 15f
                        setTextColor(
                            ContextCompat.getColor(
                                activity,
                                R.color
                                    .vico_premium_muted
                            )
                        )
                        setPadding(
                            12.dp(activity),
                            28.dp(activity),
                            12.dp(activity),
                            28.dp(activity)
                        )
                    }
                )

                return
            }

            routes.forEach {
                    server ->
                val selected =
                    server.id ==
                        activeId

                val card =
                    MaterialCardView(
                        activity
                    ).apply {
                        radius =
                            24.dp(activity)
                                .toFloat()

                        strokeWidth =
                            if (selected) {
                                2.dp(activity)
                            } else {
                                1.dp(activity)
                            }

                        strokeColor =
                            ContextCompat.getColor(
                                activity,
                                if (selected) {
                                    R.color
                                        .vico_premium_orange
                                } else {
                                    R.color
                                        .vico_premium_outline
                                }
                            )

                        setCardBackgroundColor(
                            ContextCompat.getColor(
                                activity,
                                if (selected) {
                                    R.color
                                        .vico_priority_selected
                                } else {
                                    R.color
                                        .vico_premium_card
                                }
                            )
                        )

                        isClickable = true
                        isFocusable = true

                        layoutParams =
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams
                                    .MATCH_PARENT,
                                LinearLayout.LayoutParams
                                    .WRAP_CONTENT
                            ).apply {
                                bottomMargin =
                                    10.dp(activity)
                            }

                        setOnClickListener {
                            store.setActiveServer(
                                server.id
                            )
                            onSelected(
                                server
                            )
                            dialog.dismiss()
                        }
                    }

                val row =
                    LinearLayout(
                        activity
                    ).apply {
                        orientation =
                            LinearLayout.HORIZONTAL
                        gravity =
                            Gravity.CENTER_VERTICAL

                        setPadding(
                            18.dp(activity),
                            15.dp(activity),
                            18.dp(activity),
                            15.dp(activity)
                        )
                    }

                val name =
                    TextView(
                        activity
                    ).apply {
                        text =
                            displayName(
                                server.name
                            )
                        maxLines = 2
                        textSize = 15f
                        setTextColor(
                            ContextCompat.getColor(
                                activity,
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

                val ping =
                    TextView(
                        activity
                    ).apply {
                        text =
                            if (
                                server.latencyMs > 0
                            ) {
                                "${server.latencyMs}ms"
                            } else {
                                activity.getString(
                                    R.string
                                        .vip_ping_checking
                                )
                            }

                        textDirection =
                            View.TEXT_DIRECTION_LTR
                        textSize = 14f
                        setTextColor(
                            ContextCompat.getColor(
                                activity,
                                if (
                                    server.latencyMs > 0
                                ) {
                                    R.color
                                        .vico_premium_orange
                                } else {
                                    R.color
                                        .vico_premium_muted
                                }
                            )
                        )
                    }

                row.addView(
                    name
                )
                row.addView(
                    ping
                )

                card.addView(
                    row
                )

                list.addView(
                    card
                )
            }

            AppTypography.apply(
                activity,
                list
            )
        }

        dialog.setContentView(
            root
        )

        dialog.setOnShowListener {
            dialog.findViewById<
                FrameLayout
                >(
                com.google.android.material.R.id
                    .design_bottom_sheet
            )?.setBackgroundColor(
                Color.TRANSPARENT
            )

            render()

            VipRouteOptimizer.start(
                activity,
                onUpdated = {
                    if (
                        dialog.isShowing
                    ) {
                        render()
                    }
                }
            )
        }

        AppTypography.apply(
            activity,
            root
        )

        dialog.show()
    }

    private fun displayName(
        raw: String
    ): String {
        return raw
            .substringAfterLast(
                "|"
            )
            .trim()
            .replace(
                "\u00C2\u00B7",
                "\u2022"
            )
            .replace(
                "\u00B7",
                "\u2022"
            )
            .ifBlank {
                raw.trim()
            }
    }

    private fun Int.dp(
        activity: AppCompatActivity
    ): Int {
        return (
            this *
                activity.resources
                    .displayMetrics
                    .density
            ).toInt()
    }
}
