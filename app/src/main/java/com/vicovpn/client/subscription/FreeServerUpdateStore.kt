package com.vicovpn.client.subscription

import android.content.Context
import org.json.JSONArray

class FreeServerUpdateStore(
    context: Context
) {
    companion object {
        private const val PREFERENCES_NAME =
            "free_server_update"

        private const val KEY_CANDIDATES =
            "candidates"
    }

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    fun saveCandidates(
        candidates: List<String>
    ) {
        val array = JSONArray()

        candidates.forEach {
            array.put(it)
        }

        preferences.edit()
            .putString(
                KEY_CANDIDATES,
                array.toString()
            )
            .commit()
    }

    fun loadCandidates(): List<String> {
        val value = preferences.getString(
            KEY_CANDIDATES,
            null
        ) ?: return emptyList()

        return runCatching {
            val array = JSONArray(value)

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optString(index)
                        .trim()

                    if (item.isNotBlank()) {
                        add(item)
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_CANDIDATES)
            .apply()
    }
}