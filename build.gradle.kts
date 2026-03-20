// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Update Kotlin to at least 1.9.23 to fix lock verification issues
    alias(libs.plugins.kotlin.android) apply false
    // Ensure the Compose plugin matches your Kotlin version
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}