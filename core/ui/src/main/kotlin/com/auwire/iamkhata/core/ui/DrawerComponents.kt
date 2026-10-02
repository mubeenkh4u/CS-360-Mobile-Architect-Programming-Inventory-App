package com.auwire.iamkhata.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.ThemeMode

/** Shared Auwire top bar used by feature tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuwireTopBar(
    section: String,
    onMenuClick: () -> Unit,
) {
    TopAppBar(
        title = { Text("Auwire · $section") },
        navigationIcon = {
            TextButton(onClick = onMenuClick) {
                Text("☰")
            }
        },
    )
}

/** Consistent drawer action with a visually disabled state when unavailable. */
@Composable
fun DrawerAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        },
        selected = false,
        onClick = {
            if (enabled) onClick()
        },
    )
}

/** Shared persistent appearance selector for every feature drawer. */
@Composable
fun ThemeModeSection(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    Column {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            "Appearance",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold,
        )
        ThemeMode.entries.forEach { mode ->
            NavigationDrawerItem(
                label = {
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "Use system theme"
                            ThemeMode.LIGHT -> "Light mode"
                            ThemeMode.DARK -> "Dark mode"
                        },
                    )
                },
                selected = themeMode == mode,
                onClick = { onThemeModeChange(mode) },
            )
        }
    }
}
