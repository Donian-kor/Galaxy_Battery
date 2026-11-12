import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.donian.galaxy_battery"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.donian.galaxy_battery"
        minSdk = 26
        targetSdk = 37
        // major * 10000 + minor * 100 + patch, so it always goes up with versionName
        versionCode = 20202
        versionName = "2.2.2"
    }

    signingConfigs {
        create("release") {
            val signingProps = Properties()
            val signingFile = file("$rootDir/signing.properties")
            if (signingFile.exists()) {
                signingFile.inputStream().use { inputStream ->
                    signingProps.load(inputStream)
                }
            }
            storeFile = file("$rootDir/galaxy_battery-release.jks")
            storePassword = signingProps.getProperty("galaxy.battery.release.storePassword", "")
            keyAlias = signingProps.getProperty("galaxy.battery.release.keyAlias", "galaxy-battery-key")
            keyPassword = signingProps.getProperty("galaxy.battery.release.keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Drop the libraries' other languages, or an unsupported phone would show
        // the wrong text. The app itself only ships Korean (default) and English.
        localeFilters += listOf("en")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // activity-compose brings ComponentActivity + registerForActivityResult + BackHandler
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ViewModel (needed by AppViewModel)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}