plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.fc.scanqr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fc.scanqr"
        minSdk = 23
        targetSdk = 34
        versionCode = 3
        versionName = "2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Shares the Safe release key. Credentials live outside the repo, in
            // ~/.gradle/gradle.properties; there is no fallback to the debug key.
            val storePath = providers.gradleProperty("SAFE_RELEASE_STORE_FILE").orNull
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("SAFE_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("SAFE_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("SAFE_RELEASE_KEY_PASSWORD").orNull
            }
            // minSdk 23 still needs v1 for API 23.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // ZXing for QR code generation and scanning
    implementation(libs.core)
    implementation(libs.android.integration)
    
    // CameraX dependencies
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.extensions)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}