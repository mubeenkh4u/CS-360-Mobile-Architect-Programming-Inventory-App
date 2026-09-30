package com.auwire.iamkhata

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auwire.iamkhata.core.model.WorkspaceFeatures
import com.auwire.iamkhata.feature.workspace.WorkspaceScreen
import com.auwire.iamkhata.feature.workspace.WorkspaceViewModel
import com.auwire.iamkhata.feature.workspace.WorkspaceViewModelFactory
import com.auwire.iamkhata.ui.IamKhataTheme

/** Single-activity host for independently configurable features. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.FEATURE_SCREENSHOT_PROTECTION) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val repository = (application as IamKhataApplication).container.datasetRepository

        setContent {
            IamKhataTheme {
                val workspaceViewModel: WorkspaceViewModel = viewModel(
                    factory = WorkspaceViewModelFactory(repository),
                )
                Surface(modifier = Modifier.fillMaxSize()) {
                    WorkspaceScreen(
                        viewModel = workspaceViewModel,
                        features = WorkspaceFeatures(
                            cleaning = BuildConfig.FEATURE_CLEANING,
                            pivot = BuildConfig.FEATURE_PIVOT,
                            importExport = BuildConfig.FEATURE_IMPORT_EXPORT,
                            cloudSync = BuildConfig.FEATURE_CLOUD_SYNC,
                        ),
                    )
                }
            }
        }
    }
}
