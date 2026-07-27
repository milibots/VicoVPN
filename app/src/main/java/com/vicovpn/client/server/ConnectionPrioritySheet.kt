package com.vicovpn.client.server

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.vicovpn.client.R
import com.vicovpn.client.ui.AppTypography

object ConnectionPrioritySheet {

    fun show(
        activity: AppCompatActivity,
        current: ConnectionPriorityMode,
        onSelected: (
            ConnectionPriorityMode
        ) -> Unit
    ) {
        val dialog =
            BottomSheetDialog(
                activity
            )

        val root =
            LinearLayout(activity).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    20.dp(activity),
                    12.dp(activity),
                    20.dp(activity),
                    26.dp(activity)
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
            TextView(activity).apply {
                text =
                    activity.getString(
                        R.string
                            .connection_priority_title
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
            TextView(activity).apply {
                text =
                    activity.getString(
                        R.string
                            .connection_priority_description
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
                    16.dp(activity)
                )
            }
        )

        options(activity).forEach {
                option ->
            val selected =
                option.mode ==
                    current

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
                            LinearLayout
                                .LayoutParams
                                .MATCH_PARENT,
                            LinearLayout
                                .LayoutParams
                                .WRAP_CONTENT
                        ).apply {
                            bottomMargin =
                                10.dp(activity)
                        }

                    setOnClickListener {
                        onSelected(
                            option.mode
                        )
                        dialog.dismiss()
                    }
                }

            val row =
                LinearLayout(activity).apply {
                    gravity =
                        Gravity.CENTER_VERTICAL
                    orientation =
                        LinearLayout.HORIZONTAL

                    setPadding(
                        18.dp(activity),
                        15.dp(activity),
                        18.dp(activity),
                        15.dp(activity)
                    )
                }

            val textColumn =
                LinearLayout(activity).apply {
                    orientation =
                        LinearLayout.VERTICAL

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout
                                .LayoutParams
                                .WRAP_CONTENT,
                            1f
                        )
                }

            textColumn.addView(
                TextView(activity).apply {
                    text =
                        activity.getString(
                            option.title
                        )
                    textSize = 16f
                    setTextColor(
                        ContextCompat.getColor(
                            activity,
                            R.color
                                .vico_premium_white
                        )
                    )
                }
            )

            textColumn.addView(
                TextView(activity).apply {
                    text =
                        activity.getString(
                            option.description
                        )
                    textSize = 13f
                    setTextColor(
                        ContextCompat.getColor(
                            activity,
                            R.color
                                .vico_premium_muted
                        )
                    )
                    setPadding(
                        0,
                        4.dp(activity),
                        0,
                        0
                    )
                }
            )

            row.addView(
                textColumn
            )

            if (selected) {
                row.addView(
                    TextView(activity).apply {
                        text = "✓"
                        textSize = 22f
                        gravity =
                            Gravity.CENTER
                        setTextColor(
                            ContextCompat.getColor(
                                activity,
                                R.color
                                    .vico_premium_orange
                            )
                        )
                        layoutParams =
                            LinearLayout.LayoutParams(
                                42.dp(activity),
                                42.dp(activity)
                            )
                    }
                )
            }

            card.addView(
                row
            )

            root.addView(
                card
            )
        }

        AppTypography.apply(
            activity,
            root
        )

        dialog.setContentView(
            root
        )

        dialog.setOnShowListener {
            dialog.findViewById<
                android.widget.FrameLayout
                >(
                com.google.android.material.R.id
                    .design_bottom_sheet
            )?.setBackgroundColor(
                Color.TRANSPARENT
            )
        }

        dialog.show()
    }

    private fun options(
        activity: AppCompatActivity
    ): List<Option> {
        return listOf(
            Option(
                mode =
                    ConnectionPriorityMode
                        .VIP_ONLY,
                title =
                    R.string
                        .connection_priority_vip_only,
                description =
                    R.string
                        .connection_priority_vip_only_description
            ),
            Option(
                mode =
                    ConnectionPriorityMode
                        .VIP_AND_FREE,
                title =
                    R.string
                        .connection_priority_vip_free,
                description =
                    R.string
                        .connection_priority_vip_free_description
            ),
            Option(
                mode =
                    ConnectionPriorityMode
                        .FREE_ONLY,
                title =
                    R.string
                        .connection_priority_free_only,
                description =
                    R.string
                        .connection_priority_free_only_description
            ),
            Option(
                mode =
                    ConnectionPriorityMode
                        .NONE,
                title =
                    R.string
                        .connection_priority_none,
                description =
                    R.string
                        .connection_priority_none_description
            )
        )
    }

    private data class Option(
        val mode: ConnectionPriorityMode,
        val title: Int,
        val description: Int
    )

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
