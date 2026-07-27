plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "com.subho.olikh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.subho.olikh"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
