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
        versionCode = 100
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
}
