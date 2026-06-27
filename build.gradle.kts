import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.compose") version "1.6.11"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
}

group = "com.smoothvpn"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.json:json:20240303")
}

compose.desktop {
    application {
        mainClass = "com.smoothvpn.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            includeAllModules = true
            packageName = "SmoothVPN"
            packageVersion = "1.0.0"
            description = "SmoothVPN — Xray/V2Ray client for Windows"
            vendor = "Mohammadreza Ilchi"
            windows {
                iconFile.set(project.file("icon.ico"))
                // Stable UUID so future installers upgrade in place.
                upgradeUuid = "0A8E9E2C-7C3D-4E5A-9B1F-2D6A4F8C1E77"
                menuGroup = "SmoothVPN"
                shortcut = true
            }
        }
    }
}
