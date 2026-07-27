package com.vicovpn.client.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vicovpn.client.R

data class OnboardingSlide(
    val id: String,
    @DrawableRes val mascotRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val primaryActionRes: Int,
    @StringRes val mascotDescriptionRes: Int,
    val skipAllowed: Boolean,
    val requiresAction: Boolean
)

object OnboardingSlides {
    val items =
        listOf(
            OnboardingSlide(
                id = "welcome",
                mascotRes = R.drawable.onboarding_01_welcome,
                titleRes = R.string.ob7_welcome_title,
                descriptionRes = R.string.ob7_welcome_description,
                primaryActionRes = R.string.ob7_continue,
                mascotDescriptionRes = R.string.ob7_welcome_mascot_description,
                skipAllowed = false,
                requiresAction = true
            ),
            OnboardingSlide(
                id = "free_services",
                mascotRes = R.drawable.onboarding_03_smart_server_scan,
                titleRes = R.string.ob7_free_service_title,
                descriptionRes = R.string.ob7_free_service_description,
                primaryActionRes = R.string.ob7_continue,
                mascotDescriptionRes = R.string.ob7_smart_mascot_description,
                skipAllowed = false,
                requiresAction = true
            ),
            OnboardingSlide(
                id = "privacy",
                mascotRes = R.drawable.onboarding_02_privacy_protection,
                titleRes = R.string.ob7_privacy_title,
                descriptionRes = R.string.ob7_privacy_description,
                primaryActionRes = R.string.ob7_continue,
                mascotDescriptionRes = R.string.ob7_privacy_mascot_description,
                skipAllowed = true,
                requiresAction = false
            ),
            OnboardingSlide(
                id = "smart_scan",
                mascotRes = R.drawable.onboarding_03_smart_server_scan,
                titleRes = R.string.ob7_smart_title,
                descriptionRes = R.string.ob7_smart_description,
                primaryActionRes = R.string.ob7_continue,
                mascotDescriptionRes = R.string.ob7_smart_mascot_description,
                skipAllowed = true,
                requiresAction = false
            ),
            OnboardingSlide(
                id = "speed",
                mascotRes = R.drawable.onboarding_04_speed_performance,
                titleRes = R.string.ob7_speed_title,
                descriptionRes = R.string.ob7_speed_description,
                primaryActionRes = R.string.ob7_continue,
                mascotDescriptionRes = R.string.ob7_speed_mascot_description,
                skipAllowed = true,
                requiresAction = false
            ),
            OnboardingSlide(
                id = "split_tunneling",
                mascotRes = R.drawable.onboarding_05_split_tunneling,
                titleRes = R.string.ob7_split_title,
                descriptionRes = R.string.ob7_split_description,
                primaryActionRes = R.string.ob7_continue,
                mascotDescriptionRes = R.string.ob7_split_mascot_description,
                skipAllowed = true,
                requiresAction = false
            ),
            OnboardingSlide(
                id = "permission",
                mascotRes = R.drawable.onboarding_06_connection_setup,
                titleRes = R.string.ob7_setup_title,
                descriptionRes = R.string.ob7_setup_description,
                primaryActionRes = R.string.ob7_allow_vpn_connection,
                mascotDescriptionRes = R.string.ob7_setup_mascot_description,
                skipAllowed = false,
                requiresAction = true
            ),
            OnboardingSlide(
                id = "ready",
                mascotRes = R.drawable.onboarding_07_ready_connected,
                titleRes = R.string.ob7_ready_title,
                descriptionRes = R.string.ob7_ready_description,
                primaryActionRes = R.string.ob7_start_using,
                mascotDescriptionRes = R.string.ob7_ready_mascot_description,
                skipAllowed = false,
                requiresAction = true
            )
        )
}
