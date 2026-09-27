import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared engine: the Android app uses the JVM target, the web app the JS target.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }
    js(IR) {
        outputModuleName.set("firewatch-core")
        browser()
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        jsMain.dependencies {
            // Real time zones for kotlinx-datetime in the browser.
            implementation(npm("@js-joda/timezone", "2.3.0"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
