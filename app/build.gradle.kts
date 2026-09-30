plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.auwire.iamkhata"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auwire.iamkhata"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0-data-workbench"

        buildConfigField("boolean", "FEATURE_CLEANING", "true")
        buildConfigField("boolean", "FEATURE_PIVOT", "true")
        buildConfigField("boolean", "FEATURE_SCREENSHOT_PROTECTION", "true")
        buildConfigField("boolean", "FEATURE_IMPORT_EXPORT", "false")
        buildConfigField("boolean", "FEATURE_CLOUD_SYNC", "false")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":feature:workspace"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
