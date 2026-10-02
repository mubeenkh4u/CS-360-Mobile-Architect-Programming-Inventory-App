package com.auwire.iamkhata.ui

import android.content.Context
import com.auwire.iamkhata.core.model.ThemeMode

/** Persists only the user's non-sensitive appearance preference. */
class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        "auwire_ui_preferences",
        Context.MODE_PRIVATE,
    )

    fun load(): ThemeMode =
        runCatching {
            ThemeMode.valueOf(
                preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
                    ?: ThemeMode.SYSTEM.name,
            )
        }.getOrDefault(ThemeMode.SYSTEM)

    fun save(mode: ThemeMode) {
        preferences.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
