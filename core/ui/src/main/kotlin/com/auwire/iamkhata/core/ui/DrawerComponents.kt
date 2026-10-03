package com.auwire.iamkhata.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auwire.iamkhata.core.model.ThemeMode

/**
 * Compact shared AWi&k header.
 *
 * The status-bar inset is applied once. Visual header content is top-aligned
 * directly beneath that inset, while the menu keeps a 48dp touch target that
 * extends downward instead of creating blank space above the title.
 */
@Composable
fun AuwireTopBar(
    section: String,
    onMenuClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        role = Role.Button,
                        onClick = onMenuClick,
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = "☰",
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            Text(
                text = "AWi&k · $section",
                modifier = Modifier.padding(top = 1.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
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
