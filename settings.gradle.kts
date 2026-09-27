pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_PROJECT: Kotlin/JS adds its Node.js and Yarn download repos to the root project.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
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
}

rootProject.name = "firewatch"

// Pure Kotlin logic shared by the Android app (and later the web app). Builds anywhere.
include(":core")

// The Android app needs an Android SDK. CI runners and dev machines have one; bare
// containers don't, and there only :core is built and tested.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").exists()
if (hasAndroidSdk) {
    include(":app")
}
