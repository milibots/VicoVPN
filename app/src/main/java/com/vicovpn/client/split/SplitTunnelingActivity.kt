package com.vicovpn.client.split

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vicovpn.client.R
import com.vicovpn.client.ui.AppTypography
import java.util.Locale

class SplitTunnelingActivity :
    AppCompatActivity() {

    private data class AppItem(
        val label: String,
        val packageName: String,
        val applicationInfo: ApplicationInfo
    )

    private lateinit var settings:
        SplitTunnelSettings

    private val selected =
        linkedSetOf<String>()

    private var allApps =
        emptyList<AppItem>()

    private lateinit var adapter:
        AppAdapter

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        settings =
            SplitTunnelSettings(this)

        selected +=
            settings.getSelectedPackages()

        setContentView(
            buildContent()
        )

        loadApps()
    }

    override fun onPause() {
        settings.setSelectedPackages(
            selected
        )
        super.onPause()
    }

    private fun buildContent(): View {
        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    20.dp,
                    36.dp,
                    20.dp,
                    18.dp
                )
                setBackgroundColor(
                    color(
                        R.color
                            .vico_premium_background
                    )
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
            ImageButton(this).apply {
                setImageResource(
                    R.drawable.ic_arrow_back
                )
                imageTintList =
                    android.content.res
                        .ColorStateList.valueOf(
                            color(
                                R.color
                                    .vico_premium_white
                            )
                        )
                setBackgroundColor(
                    android.graphics.Color
                        .TRANSPARENT
                )
                contentDescription =
                    getString(
                        R.string.back
                    )
                setOnClickListener {
                    finish()
                }
            },
            LinearLayout.LayoutParams(
                48.dp,
                48.dp
            )
        )

        header.addView(
            TextView(this).apply {
                text =
                    getString(
                        R.string.split_tunneling
                    )
                setTextColor(
                    color(
                        R.color
                            .vico_premium_white
                    )
                )
                textSize = 24f
                typeface =
                    Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(header)

        root.addView(
            TextView(this).apply {
                text =
                    getString(
                        R.string
                            .split_tunneling_description
                    )
                setTextColor(
                    color(
                        R.color
                            .vico_premium_muted
                    )
                )
                textSize = 14f
                setPadding(
                    0,
                    8.dp,
                    0,
                    14.dp
                )
            }
        )

        val group =
            RadioGroup(this).apply {
                orientation =
                    RadioGroup.VERTICAL
                setPadding(
                    12.dp,
                    8.dp,
                    12.dp,
                    8.dp
                )
                background =
                    ContextCompat.getDrawable(
                        this@SplitTunnelingActivity,
                        R.drawable
                            .bg_premium_input
                    )
            }

        val modes =
            listOf(
                SplitTunnelMode.ALL_APPS to
                    R.string.split_all_apps,
                SplitTunnelMode.EXCLUDE_SELECTED to
                    R.string.split_exclude_selected,
                SplitTunnelMode.INCLUDE_SELECTED to
                    R.string.split_include_selected
            )

        modes.forEach {
                (mode, title) ->
            group.addView(
                RadioButton(this).apply {
                    id =
                        View.generateViewId()
                    tag = mode
                    text =
                        getString(title)
                    setTextColor(
                        color(
                            R.color
                                .vico_premium_white
                        )
                    )
                    textSize = 15f
                    isChecked =
                        settings.getMode() ==
                            mode
                    buttonTintList =
                        android.content.res
                            .ColorStateList.valueOf(
                                color(
                                    R.color
                                        .vico_premium_orange
                                )
                            )
                }
            )
        }

        group.setOnCheckedChangeListener {
                radioGroup,
                checkedId ->
            val button =
                radioGroup.findViewById<RadioButton>(
                    checkedId
                )
            val mode =
                button?.tag as?
                    SplitTunnelMode
                ?: return@setOnCheckedChangeListener
            settings.setMode(mode)
        }

        root.addView(group)

        val search =
            EditText(this).apply {
                hint =
                    getString(
                        R.string.search_apps
                    )
                setSingleLine(true)
                setTextColor(
                    color(
                        R.color
                            .vico_premium_white
                    )
                )
                setHintTextColor(
                    color(
                        R.color
                            .vico_premium_muted
                    )
                )
                setPadding(
                    18.dp,
                    0,
                    18.dp,
                    0
                )
                background =
                    ContextCompat.getDrawable(
                        this@SplitTunnelingActivity,
                        R.drawable
                            .bg_premium_input
                    )
            }

        root.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                54.dp
            ).apply {
                topMargin = 14.dp
                bottomMargin = 10.dp
            }
        )

        adapter =
            AppAdapter()

        val list =
            ListView(this).apply {
                divider = null
                dividerHeight = 0
                clipToPadding = false
                setPadding(
                    0,
                    0,
                    0,
                    20.dp
                )
                this.adapter =
                    this@SplitTunnelingActivity
                        .adapter
            }

        root.addView(
            list,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        search.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    value: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    value: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    adapter.filter(
                        value?.toString()
                            .orEmpty()
                    )
                }

                override fun afterTextChanged(
                    value: Editable?
                ) = Unit
            }
        )

        root.addView(
            TextView(this).apply {
                text =
                    getString(
                        R.string
                            .split_reconnect_note
                    )
                setTextColor(
                    color(
                        R.color
                            .vico_premium_muted
                    )
                )
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(
                    0,
                    8.dp,
                    0,
                    0
                )
            }
        )

        AppTypography.apply(
            this,
            root
        )

        return root
    }

    private fun loadApps() {
        val launcher =
            Intent(
                Intent.ACTION_MAIN
            ).addCategory(
                Intent.CATEGORY_LAUNCHER
            )

        val resolved =
            if (
                Build.VERSION.SDK_INT >= 33
            ) {
                packageManager.queryIntentActivities(
                    launcher,
                    PackageManager
                        .ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(
                    launcher,
                    0
                )
            }

        allApps =
            resolved.asSequence()
                .map {
                    it.activityInfo
                        .applicationInfo
                }
                .filter {
                    it.packageName !=
                        packageName
                }
                .distinctBy {
                    it.packageName
                }
                .map {
                    AppItem(
                        label =
                            packageManager
                                .getApplicationLabel(it)
                                .toString(),
                        packageName =
                            it.packageName,
                        applicationInfo = it
                    )
                }
                .sortedBy {
                    it.label.lowercase(
                        Locale.getDefault()
                    )
                }
                .toList()

        adapter.setItems(
            allApps
        )
    }

    private inner class AppAdapter :
        BaseAdapter() {

        private var visibleItems =
            emptyList<AppItem>()

        fun setItems(
            items: List<AppItem>
        ) {
            visibleItems = items
            notifyDataSetChanged()
        }

        fun filter(
            query: String
        ) {
            val normalized =
                query.trim()
                    .lowercase(
                        Locale.getDefault()
                    )

            visibleItems =
                if (normalized.isBlank()) {
                    allApps
                } else {
                    allApps.filter {
                        it.label.lowercase(
                            Locale.getDefault()
                        ).contains(normalized) ||
                            it.packageName
                                .lowercase(
                                    Locale.getDefault()
                                )
                                .contains(normalized)
                    }
                }

            notifyDataSetChanged()
        }

        override fun getCount(): Int =
            visibleItems.size

        override fun getItem(
            position: Int
        ): AppItem =
            visibleItems[position]

        override fun getItemId(
            position: Int
        ): Long =
            getItem(position)
                .packageName.hashCode()
                .toLong()

        override fun getView(
            position: Int,
            recycled: View?,
            parent: ViewGroup?
        ): View {
            val item =
                getItem(position)

            val row =
                (recycled as?
                    LinearLayout)
                    ?: createRow()

            val icon =
                row.getChildAt(0) as
                    ImageView

            val textContainer =
                row.getChildAt(1) as
                    LinearLayout

            val title =
                textContainer.getChildAt(0) as
                    TextView

            val packageText =
                textContainer.getChildAt(1) as
                    TextView

            val check =
                row.getChildAt(2) as
                    CheckBox

            icon.setImageDrawable(
                packageManager
                    .getApplicationIcon(
                        item.applicationInfo
                    )
            )

            title.text = item.label
            packageText.text =
                item.packageName
            check.isChecked =
                item.packageName in
                    selected

            val toggle = {
                if (
                    item.packageName in
                    selected
                ) {
                    selected -=
                        item.packageName
                } else {
                    selected +=
                        item.packageName
                }

                settings.setSelectedPackages(
                    selected
                )

                check.isChecked =
                    item.packageName in
                        selected
            }

            row.setOnClickListener {
                toggle()
            }

            check.setOnClickListener {
                toggle()
            }

            AppTypography.apply(
                this@SplitTunnelingActivity,
                row
            )

            return row
        }

        private fun createRow():
            LinearLayout {
            return LinearLayout(
                this@SplitTunnelingActivity
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(
                    12.dp,
                    8.dp,
                    8.dp,
                    8.dp
                )

                addView(
                    ImageView(
                        this@SplitTunnelingActivity
                    ).apply {
                        scaleType =
                            ImageView.ScaleType
                                .CENTER_CROP
                    },
                    LinearLayout.LayoutParams(
                        44.dp,
                        44.dp
                    )
                )

                addView(
                    LinearLayout(
                        this@SplitTunnelingActivity
                    ).apply {
                        orientation =
                            LinearLayout.VERTICAL
                        setPadding(
                            12.dp,
                            0,
                            8.dp,
                            0
                        )

                        addView(
                            TextView(
                                this@SplitTunnelingActivity
                            ).apply {
                                setTextColor(
                                    color(
                                        R.color
                                            .vico_premium_white
                                    )
                                )
                                textSize = 15f
                            }
                        )

                        addView(
                            TextView(
                                this@SplitTunnelingActivity
                            ).apply {
                                setTextColor(
                                    color(
                                        R.color
                                            .vico_premium_muted
                                    )
                                )
                                textSize = 11f
                                maxLines = 1
                            }
                        )
                    },
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                addView(
                    CheckBox(
                        this@SplitTunnelingActivity
                    ).apply {
                        buttonTintList =
                            android.content.res
                                .ColorStateList.valueOf(
                                    color(
                                        R.color
                                            .vico_premium_orange
                                    )
                                )
                    },
                    LinearLayout.LayoutParams(
                        48.dp,
                        48.dp
                    )
                )
            }
        }
    }

    private fun color(
        resource: Int
    ): Int =
        ContextCompat.getColor(
            this,
            resource
        )

    private val Int.dp: Int
        get() =
            (this *
                resources.displayMetrics.density)
                .toInt()
}
