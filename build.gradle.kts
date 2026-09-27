// The Android Gradle Plugin is only put on the classpath when the Android app is part of
// the build (see settings.gradle.kts), so :core can be built without Google's Maven repo.
buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
    dependencies {
        if (findProject(":app") != null) {
            classpath("com.android.tools.build:gradle:8.13.0")
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
