plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dependency.check)
}

android {
    namespace = "com.cundalf.wavelink"
    // compileSdk 37: lo requieren core-ktx 1.19 y Compose 1.12 (BOM 2026.09).
    // targetSdk se mantiene en 36 a propósito: optimización separada del compileSdk.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cundalf.wavelink"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.1"

        val castAppId = (project.findProperty("subwave.castAppId") as String?) ?: ""
        // Solo para pruebas: habilita el botón de cast con el receiver por defecto
        // de Google (sin registro). Para publicar en Play hace falta un receiver ID propio.
        val castAllowDefault =
            (project.findProperty("subwave.castAllowDefault") as String?)?.toBoolean() ?: false
        buildConfigField("String", "CAST_APP_ID", "\"$castAppId\"")
        buildConfigField("boolean", "CAST_ALLOW_DEFAULT", castAllowDefault.toString())
        resValue("string", "cast_receiver_id", castAppId)
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // AGP 9 desactiva resValues por defecto; la app la usa para cast_receiver_id.
        resValues = true
    }

    lint {
        // Media3 (session/cast) marca muchas APIs como @UnstableApi; el opt-in ya se
        // declara en el código. Este chequeo propaga el requisito a todos los callers.
        disable += "UnsafeOptInUsageError"

        abortOnError = true
        checkDependencies = false
        xmlReport = true
        htmlReport = true
        warningsAsErrors = false
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    parallel = true
}

ktlint {
    android.set(true)
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    skip =
        providers
            .environmentVariable("DEPENDENCY_CHECK_SKIP")
            .map { it.equals("true", ignoreCase = true) }
            .getOrElse(false)
}

// Con built-in Kotlin (AGP 9) el jvmTarget de Kotlin toma por defecto el
// targetCompatibility de compileOptions (17), no hace falta configurarlo.

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.cast)
    implementation(libs.play.services.cast.framework)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.mediarouter)

    detektPlugins(libs.detekt.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    // Los tests de render Compose necesitan el Activity de ui-test-manifest,
    // que solo se agrega al variant debug.
    testDebugImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
