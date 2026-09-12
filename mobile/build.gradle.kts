// Root build file. No plugins are applied here — each subproject applies what it needs.
// Declaring plugin versions via the version catalog (with apply false) lets subprojects
// use `alias(libs.plugins.xxx)` without re-specifying versions.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}
