plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sushanth.poplauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sushanth.poplauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("en")
    }

    signingConfigs {
        create("playRelease") {
            val storeFilePath = providers.environmentVariable("PLAY_UPLOAD_STORE_FILE").orNull
            val storePasswordValue = providers.environmentVariable("PLAY_UPLOAD_STORE_PASSWORD").orNull
            val keyAliasValue = providers.environmentVariable("PLAY_UPLOAD_KEY_ALIAS").orNull
            val keyPasswordValue = providers.environmentVariable("PLAY_UPLOAD_KEY_PASSWORD").orNull

            if (!storeFilePath.isNullOrBlank() && !storePasswordValue.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val envKeys = listOf(
                "PLAY_UPLOAD_STORE_FILE",
                "PLAY_UPLOAD_STORE_PASSWORD",
                "PLAY_UPLOAD_KEY_ALIAS",
                "PLAY_UPLOAD_KEY_PASSWORD"
            )
            var hasPlaySigning = true
            for (key in envKeys) {
                if (providers.environmentVariable(key).orNull.isNullOrBlank()) {
                    hasPlaySigning = false
                    break
                }
            }
            if (hasPlaySigning) {
                signingConfig = signingConfigs.getByName("playRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}