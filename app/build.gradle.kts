plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

android {
    namespace = "tv.dugout"
    compileSdk = 37

    defaultConfig {
        applicationId = "tv.dugout"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        // Upstream releases must not fail the gate; version bumps are a deliberate change.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.tv.material)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.robolectric)
}

kover {
    reports {
        filters {
            excludes {
                // Generated code and color tokens only; screens and logic stay measured.
                classes("tv.dugout.BuildConfig", "tv.dugout.R", "tv.dugout.R$*", "*ComposableSingletons*")
                classes("tv.dugout.ui.theme.ColorKt")
            }
        }
        variant("debug") {
            verify {
                rule {
                    minBound(90)
                }
            }
        }
    }
}
