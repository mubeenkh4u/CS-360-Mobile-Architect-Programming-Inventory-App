package com.auwire.iamkhata

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.auwire.iamkhata.ui.IamKhataTheme

/** Single-activity host for independently configurable business features. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.FEATURE_SCREENSHOT_PROTECTION) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val container = (application as IamKhataApplication).container
        setContent {
            IamKhataTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(container)
                }
            }
        }
    }
}
