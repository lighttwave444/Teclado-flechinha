plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.flechinha.teclado"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.flechinha.teclado"
        minSdk = 21
        targetSdk = 34
        versionCode = 5
        versionName = "5.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
