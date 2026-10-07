package com.auwire.iamkhata.core.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Layout choices shared by data-heavy AWi&k feature screens. */
enum class ContentViewLayout {
    CARDS,
    TABLE,
}

/** Stable preference keys so each feature remembers its own layout. */
object ViewLayoutPreferenceKey {
    const val KHATA = "khata_view_layout"
    const val INVENTORY = "inventory_view_layout"
}

/**
 * Observable layout state backed by SharedPreferences.
 *
 * Preference writes happen immediately when the user changes layouts, so the
 * choice survives both feature-tab disposal/recreation and process restarts.
 */
@Stable
class PersistentContentViewLayoutState internal constructor(
    initialValue: ContentViewLayout,
    private val persist: (ContentViewLayout) -> Unit,
) {
    var value by mutableStateOf(initialValue)
        private set

    fun select(newValue: ContentViewLayout) {
        if (value == newValue) return
        value = newValue
        persist(newValue)
    }
}

@Composable
fun rememberPersistentContentViewLayout(
    preferenceKey: String,
    defaultValue: ContentViewLayout,
): PersistentContentViewLayoutState {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            VIEW_LAYOUT_PREFERENCES,
            Context.MODE_PRIVATE,
        )
    }

    return remember(preferences, preferenceKey, defaultValue) {
        val storedValue = preferences
            .getString(preferenceKey, null)
            ?.let { stored ->
                runCatching { ContentViewLayout.valueOf(stored) }.getOrNull()
            }
            ?: defaultValue

        PersistentContentViewLayoutState(storedValue) { layout ->
            preferences
                .edit()
                .putString(preferenceKey, layout.name)
                .apply()
        }
    }
}

private const val VIEW_LAYOUT_PREFERENCES = "awik_view_layout_preferences"
