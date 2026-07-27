package com.vicovpn.client.onboarding

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.vicovpn.client.R
import com.vicovpn.client.ui.AppTypography
import kotlin.math.roundToInt

class OnboardingAdapter(
    private val slides: List<OnboardingSlide>,
    private val selectedLanguage: () -> String?,
    private val selectedFreeMode: () -> Boolean?,
    private val onLanguageSelected: (String) -> Unit,
    private val onFreeModeSelected: (Boolean) -> Unit,
    private val onPrimary: (Int) -> Unit,
    private val onBack: (Int) -> Unit,
    private val onSkip: (Int) -> Unit
) : RecyclerView.Adapter<
    OnboardingAdapter.SlideHolder
    >() {

    private var permissionDenied = false
    private var actionPending = false
    private var discoveryState =
        OnboardingDiscoveryCoordinator.State.IDLE

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SlideHolder {
        return SlideHolder(
            LayoutInflater.from(
                parent.context
            ).inflate(
                R.layout.item_onboarding_slide,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int =
        slides.size

    override fun onBindViewHolder(
        holder: SlideHolder,
        position: Int
    ) {
        holder.bind(
            slide = slides[position],
            position = position
        )
    }

    fun setPermissionDenied(
        denied: Boolean
    ) {
        permissionDenied = denied
        notifyItemChanged(6)
    }

    fun setActionPending(
        pending: Boolean
    ) {
        actionPending = pending
        notifyItemChanged(6)
    }

    fun setDiscoveryState(
        state: OnboardingDiscoveryCoordinator.State
    ) {
        discoveryState = state
        notifyItemRangeChanged(
            2,
            itemCount - 2
        )
    }

    fun refreshLanguage() {
        notifyItemChanged(0)
    }

    fun refreshFreeChoice() {
        notifyItemChanged(1)
    }

    inner class SlideHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(
        itemView
    ) {
        private val back: ImageButton =
            itemView.findViewById(
                R.id.obBackButton
            )

        private val skip: TextView =
            itemView.findViewById(
                R.id.obSkipButton
            )

        private val progress: LinearProgressIndicator =
            itemView.findViewById(
                R.id.obProgress
            )

        private val mascot: ImageView =
            itemView.findViewById(
                R.id.obMascot
            )

        private val title: TextView =
            itemView.findViewById(
                R.id.obTitle
            )

        private val description: TextView =
            itemView.findViewById(
                R.id.obDescription
            )

        private val languageChoices: LinearLayout =
            itemView.findViewById(
                R.id.obLanguageChoices
            )

        private val persian: MaterialButton =
            itemView.findViewById(
                R.id.obPersianButton
            )

        private val english: MaterialButton =
            itemView.findViewById(
                R.id.obEnglishButton
            )

        private val discovery: TextView =
            itemView.findViewById(
                R.id.obDiscoveryStatus
            )

        private val permissionMessage: TextView =
            itemView.findViewById(
                R.id.obPermissionMessage
            )

        private val primary: MaterialButton =
            itemView.findViewById(
                R.id.obPrimaryButton
            )

        fun bind(
            slide: OnboardingSlide,
            position: Int
        ) {
            val context = itemView.context

            progress.max = slides.size
            progress.progress = position + 1
            progress.contentDescription =
                context.getString(
                    R.string.ob7_progress_accessibility,
                    position + 1,
                    slides.size
                )

            ViewCompat.setAccessibilityLiveRegion(
                progress,
                ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE
            )

            back.visibility =
                if (position > 0) View.VISIBLE else View.INVISIBLE

            skip.visibility =
                if (slide.skipAllowed) View.VISIBLE else View.INVISIBLE

            mascot.setImageResource(slide.mascotRes)
            mascot.contentDescription =
                context.getString(slide.mascotDescriptionRes)

            val screenHeight =
                context.resources.displayMetrics.heightPixels
            val density =
                context.resources.displayMetrics.density
            val desired =
                (screenHeight * 0.35f).roundToInt()
            val minimum =
                (200f * density).roundToInt()
            val maximum =
                (360f * density).roundToInt()

            mascot.layoutParams =
                mascot.layoutParams.apply {
                    height = desired.coerceIn(
                        minimum,
                        maximum
                    )
                }

            mascot.scaleType = ImageView.ScaleType.FIT_CENTER

            title.setText(slide.titleRes)
            description.setText(slide.descriptionRes)
            primary.setText(slide.primaryActionRes)

            primary.isEnabled =
                !actionPending &&
                    when (position) {
                        0 -> selectedLanguage() != null
                        1 -> selectedFreeMode() != null
                        else -> true
                    }

            languageChoices.visibility =
                if (position == 0 || position == 1) View.VISIBLE else View.GONE

            permissionMessage.visibility =
                if (position == 6 && permissionDenied) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            if (position == 6 && permissionDenied) {
                primary.setText(R.string.ob7_retry_permission)
            }

            discovery.visibility =
                if (
                    position > 1 &&
                    discoveryState !=
                    OnboardingDiscoveryCoordinator.State.IDLE
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            discovery.setText(
                when (discoveryState) {
                    OnboardingDiscoveryCoordinator.State.READY ->
                        R.string.ob7_background_ready

                    OnboardingDiscoveryCoordinator.State.FAILED ->
                        R.string.ob7_background_retry_later

                    else ->
                        R.string.ob7_background_preparing
                }
            )

            bindChoiceButtons(position)

            back.setOnClickListener {
                onBack(bindingAdapterPosition)
            }

            skip.setOnClickListener {
                onSkip(bindingAdapterPosition)
            }

            primary.setOnClickListener {
                onPrimary(bindingAdapterPosition)
            }

            persian.setOnClickListener {
                when (position) {
                    0 -> onLanguageSelected("fa")
                    1 -> onFreeModeSelected(true)
                }
            }

            english.setOnClickListener {
                when (position) {
                    0 -> onLanguageSelected("en")
                    1 -> onFreeModeSelected(false)
                }
            }

            AppTypography.apply(
                context,
                itemView
            )

            if (
                position == 4 &&
                ValueAnimator.areAnimatorsEnabled()
            ) {
                mascot.animate().cancel()
                mascot.alpha = 0f
                mascot.translationX = 10f * density
                mascot.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(280L)
                    .start()
            } else {
                mascot.alpha = 1f
                mascot.translationX = 0f
            }
        }

        private fun bindChoiceButtons(position: Int) {
            val context = itemView.context

            if (position == 0) {
                persian.setText(R.string.ob7_language_persian)
                english.setText(R.string.ob7_language_english)

                val language = selectedLanguage()

                styleChoiceButton(
                    button = persian,
                    selected = language == "fa"
                )

                styleChoiceButton(
                    button = english,
                    selected = language == "en"
                )

                languageChoices.contentDescription =
                    context.getString(
                        R.string.ob7_language_group_description
                    )
            } else if (position == 1) {
                persian.setText(R.string.ob7_free_service_yes)
                english.setText(R.string.ob7_free_service_no)

                val freeMode = selectedFreeMode()

                styleChoiceButton(
                    button = persian,
                    selected = freeMode == true
                )

                styleChoiceButton(
                    button = english,
                    selected = freeMode == false
                )

                languageChoices.contentDescription =
                    context.getString(
                        R.string.ob7_free_service_group_description
                    )
            }
        }

        private fun styleChoiceButton(
            button: MaterialButton,
            selected: Boolean
        ) {
            val context = itemView.context

            button.setBackgroundResource(
                if (selected) {
                    R.drawable.bg_onboarding_language_selected
                } else {
                    R.drawable.bg_onboarding_language_unselected
                }
            )

            button.backgroundTintList = null
            button.strokeWidth = 0

            button.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (selected) {
                        R.color.vico_premium_white
                    } else {
                        R.color.vico_premium_muted
                    }
                )
            )

            button.iconTint =
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        if (selected) {
                            R.color.vico_premium_orange
                        } else {
                            R.color.vico_premium_muted
                        }
                    )
                )

            button.contentDescription =
                button.text.toString() +
                    if (selected) {
                        ", " +
                            context.getString(
                                R.string.ob7_language_selected
                            )
                    } else {
                        ""
                    }
        }
    }
}
