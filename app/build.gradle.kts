import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Versioning: see version.properties. Releases use releaseNumber * 10. The release workflow
// rebuilds the previous release as a rollback APK with -PversionCodeOverride=<N*10+5> -Prollback=true.
// These properties must keep working in every future version.
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val releaseNumber = versionProps.getProperty("releaseNumber").trim().toInt()
val baseVersionName = versionProps.getProperty("versionName").trim()
val isRollbackBuild = (findProperty("rollback") as String?)?.toBoolean() ?: false
val versionCodeOverride = (findProperty("versionCodeOverride") as String?)?.toInt()

// Where the app looks for updates. Public, on purpose.
val updateManifestUrl = (findProperty("updateManifestUrl") as String?)
    ?: "https://github.com/dylancleverdon/Nicotine-Quitting-app/releases/download/apk-latest/update.json"

android {
    namespace = "com.baastiklabs.firewatch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.baastiklabs.firewatch"
        minSdk = 26
        // Silent self-updates require a recent targetSdk. Keep this current every year.
        targetSdk = 36
        versionCode = versionCodeOverride ?: (releaseNumber * 10)
        versionName = if (isRollbackBuild) "$baseVersionName (rollback)" else baseVersionName
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$updateManifestUrl\"")
        buildConfigField("boolean", "IS_ROLLBACK_BUILD", "$isRollbackBuild")
        buildConfigField("boolean", "E2E", "false")
    }

    // One permanent signing key, committed on purpose: every future version must be signed with
    // it or it can't install over the last one. Never regenerate or replace it.
    signingConfigs {
        create("firewatch") {
            storeFile = rootProject.file("app/signing/firewatch.jks")
            storePassword = "baastiklabs-firewatch"
            keyAlias = "firewatch"
            keyPassword = "baastiklabs-firewatch"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("firewatch")
        }
        debug {
            signingConfig = signingConfigs.getByName("firewatch")
        }
        // Identical to release, but checks a local test server and has an adb-triggerable
        // receiver. Used only by the updater end-to-end test in CI.
        create("e2e") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("String", "UPDATE_MANIFEST_URL", "\"http://10.0.2.2:8765/update.json\"")
            buildConfigField("boolean", "E2E", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.work.runtime)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glance.appwidget)
    implementation(libs.health.connect)

    testImplementation(libs.junit)
}
