package com.auwire.iamkhata

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.auwire.iamkhata.ui.AuwireTheme
import com.auwire.iamkhata.ui.ThemePreferences

/** Single-activity host for independently configurable business features. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Edge-to-edge makes the status-bar relationship explicit. Feature
         * headers apply statusBarsPadding() once, eliminating the doubled inset
         * visible in earlier builds.
         */
        enableEdgeToEdge()

        if (BuildConfig.FEATURE_SCREENSHOT_PROTECTION) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val container = (application as IamKhataApplication).container
        val themePreferences = ThemePreferences(this)

        setContent {
            var themeMode by remember { mutableStateOf(themePreferences.load()) }

            AuwireTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        container = container,
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themePreferences.save(mode)
                            themeMode = mode
                        },
                    )
                }
            }
        }
    }
}
