// Root build file. No plugins are applied here — each subproject applies what it needs.
// Declaring plugin versions via the version catalog (with apply false) lets subprojects
// use `alias(libs.plugins.xxx)` without re-specifying versions.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    // Task 1 (:androidApp): declared here (apply false) so both plugin markers resolve once, with
    // the version pinned in the catalog; actually applied (not apply false) in androidApp/build.gradle.kts.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinCompose) apply false
}
