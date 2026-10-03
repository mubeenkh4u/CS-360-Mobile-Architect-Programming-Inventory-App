package com.auwire.iamkhata.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.ThemeMode

/**
 * Compact shared AWi&k header.
 *
 * MainActivity renders edge-to-edge. statusBarsPadding() therefore applies the
 * system-bar inset exactly once, avoiding the previous double top padding.
 */
@Composable
fun AuwireTopBar(
    section: String,
    onMenuClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onMenuClick,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("☰", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "AWi&k · $section",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

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
