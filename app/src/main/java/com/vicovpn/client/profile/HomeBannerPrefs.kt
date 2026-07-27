package com.vicovpn.client.profile

import android.content.Context

object HomeBannerPrefs {
    private const val PREFS = "vico_home_preferences"
    private const val KEY_HOME_BANNERS = "home_banners_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HOME_BANNERS, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HOME_BANNERS, enabled)
            .apply()
    }
}
