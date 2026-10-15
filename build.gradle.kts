buildscript {
    dependencies {
        // AGP 9 trae KGP 2.2.10 embebido (built-in Kotlin); se sube explícitamente
        // a la versión del catálogo para tomar los fixes de seguridad del toolchain
        // (CVE-2026-53914) y mantener Compose compiler alineado. Ver:
        // https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade-to-a-higher-kgp-version
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.dependency.check) apply false
    alias(libs.plugins.ben.manes.versions)
}
