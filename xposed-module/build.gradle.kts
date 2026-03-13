plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lilstiffy.mockgps.xposed"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lilstiffy.mockgps.xposed"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Xposed API - compileOnly so it doesn't get bundled (provided by Xposed framework at runtime)
    compileOnly("de.robv.android.xposed:api:82")
}
