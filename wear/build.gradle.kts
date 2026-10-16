plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.letheapp.nocturn.watch"
    compileSdk = 36

    defaultConfig {
        // Eigenständige App (kein Play-Store-Feature-Modul, siehe CLAUDE.md "keine Play-Store-
        // Veröffentlichung") — wird separat per ADB/Dateimanager auf die Uhr übertragen, daher
        // eigene applicationId statt eines an :app gekoppelten Wear-Feature-Moduls.
        applicationId = "de.letheapp.nocturn.watch"
        // minSdk 30 deckt Wear OS 3 UND 4 ab (Nutzeranforderung: WearOS 4), targetSdk 34 ist die
        // von Google für aktuelle Wear-Apps empfohlene Baseline.
        minSdk = 30
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)

    // Dieselbe Wear-Data-Layer-API wie im Handy-Modul (siehe app/build.gradle.kts) — liest hier
    // die von de.letheapp.nocturn.wear.WearSyncManager übertragene Agenda.
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
}
