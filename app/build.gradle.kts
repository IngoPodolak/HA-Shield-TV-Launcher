plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val repoName = System.getenv("GITHUB_REPOSITORY") ?: "IngoPodolak/shield-launcher"

android {
    namespace = "de.ingo.tvhome"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.ingo.tvhome"
        minSdk = 26
        targetSdk = 33
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        buildConfigField("String", "REPO", "\"$repoName\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "tvhome123"
            keyAlias = "tvhome"
            keyPassword = "tvhome123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("jpg")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
}
