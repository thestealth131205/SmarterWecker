import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release-Signierung: Keystore-Pfad/Passwörter kommen lokal aus local.properties (bereits
// gitignored), im CI aus GitHub-Secrets als gleichnamige Umgebungsvariablen — analog zum
// OPENWEATHER_API_KEY-Handling aus dem Iosync-Projekt (siehe CLAUDE.md). Der Keystore selbst
// liegt unter keystore/ (ebenfalls gitignored), niemals im Repo.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

fun signingProperty(key: String): String? = localProperties.getProperty(key) ?: System.getenv(key)

val releaseStoreFilePath = signingProperty("RELEASE_KEYSTORE_PATH")

android {
    namespace = "de.letheapp.nocturn"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.letheapp.nocturn"
        // minSdk bleibt 33 (Android 13) — Abwärtskompatibilität ist Pflicht. Nur compileSdk/
        // targetSdk werden angehoben (Android 16 / API 36), damit die App auf aktuellen Geräten
        // (Android 14/15/16/17) ohne Kompatibilitätsmodus läuft. Höher als 36 ginge nur mit AGP 9
        // + Gradle 9 + Kotlin 2.2/KSP2 (API 37/Android 17) — bewusst (noch) nicht gemacht.
        minSdk = 33
        targetSdk = 36
        versionCode = 39
        versionName = "0.38.0"

        // OpenWeatherMap-Key-Handling analog zum Iosync-Projekt: lokal aus local.properties,
        // im CI aus dem GitHub-Secret OPENWEATHER_API_KEY als gleichnamige Umgebungsvariable.
        val owmKey = localProperties.getProperty("OPENWEATHER_API_KEY", "")
        val apiKey = System.getenv("OPENWEATHER_API_KEY") ?: owmKey
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$apiKey\"")
    }

    signingConfigs {
        if (releaseStoreFilePath != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath)
                storePassword = signingProperty("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = signingProperty("RELEASE_KEY_ALIAS")
                keyPassword = signingProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }
}

// Room exportiert bei jedem Build einen Schema-Snapshot nach app/schemas/ — Grundlage, um
// künftige Versionsbumps per echter Migration (statt destruktivem Neuaufbau) zu überprüfen,
// siehe NocturnDatabase.kt und DatabaseModule.kt.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.health.connect.client)

    implementation(libs.tesseract4android)

    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Für die Wear-Data-Layer-Kommunikation mit einer gekoppelten WearOS-Smartwatch (siehe
    // de.letheapp.nocturn.wear). Bewusste Ausnahme von der sonstigen FOSS-Minimalismus-Leitlinie
    // (siehe CLAUDE.md): die Wearable-Data-Layer-API ist Teil von Google Play Services (closed-
    // source) und es gibt keinen praktikablen FOSS-Ersatz, der zuverlässig Handy↔Uhr kommuniziert
    // (analog zur bereits bestehenden Health-Connect-Abhängigkeit).
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
}
