import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use(::load)
    }
}

fun nonBlank(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }

fun signingValue(propertyName: String, environmentName: String): String? =
    nonBlank(signingProperties.getProperty(propertyName))
        ?: nonBlank(System.getenv(environmentName))

val releaseStoreFile = signingValue("storeFile", "AUWIRE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "AUWIRE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "AUWIRE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "AUWIRE_KEY_PASSWORD")

val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.auwire.iamkhata"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auwire.iamkhata"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "6.2.0"

        buildConfigField("boolean", "FEATURE_CLEANING", "true")
        buildConfigField("boolean", "FEATURE_PIVOT", "true")
        buildConfigField("boolean", "FEATURE_INVENTORY", "true")
        buildConfigField("boolean", "FEATURE_TENTATIVE_STOCK", "true")
        buildConfigField("boolean", "ALLOW_NEGATIVE_COMMITTED_STOCK", "false")
        buildConfigField("boolean", "FEATURE_SCREENSHOT_PROTECTION", "true")
        buildConfigField("boolean", "FEATURE_IMPORT_EXPORT", "true")
        buildConfigField("boolean", "FEATURE_CLOUD_SYNC", "false")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation(project(":feature:inventory"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
