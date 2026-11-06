import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// CI passes GITHUB_RUN_NUMBER; local builds fall back to 1.
val runNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.gleaped.italianlearning"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.gleaped.italianlearning"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = runNumber
        versionName = "0.1.$runNumber"
    }

    signingConfigs {
        // Personal sideload key, committed on purpose so every CI build has the
        // same signature (required for Obtainium updates). NOT a Play Store key.
        // See README "Signing" for how to swap it for a secret-based key.
        create("sideload") {
            val ksPath = System.getenv("ITALIANO_KEYSTORE_PATH")
            storeFile = if (ksPath.isNullOrBlank()) file("signing/italiano-sideload.jks") else file(ksPath)
            storePassword = System.getenv("ITALIANO_KEYSTORE_PASSWORD") ?: "italiano-debug"
            keyAlias = System.getenv("ITALIANO_KEY_ALIAS") ?: "italiano"
            keyPassword = System.getenv("ITALIANO_KEY_PASSWORD") ?: "italiano-debug"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("sideload")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        // Version-bump nags are not actionable in CI.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
