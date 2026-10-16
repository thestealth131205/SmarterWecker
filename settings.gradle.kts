pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Für Tesseract4Android (FOSS-OCR-Lib für den Foto-Import, Kernfunktion 5),
        // wird nicht auf Maven Central veröffentlicht, nur über JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NocTurn"
include(":app")
include(":wear")
