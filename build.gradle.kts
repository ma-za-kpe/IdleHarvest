plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.googleServices) apply false
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(
            mapOf(
                // Line-length delegated to Detekt (which excludes comments); ktlint defers.
                "max_line_length" to "off",
                // Compose @Composable functions follow PascalCase by convention.
                "ktlint_standard_function-naming" to "disabled",
                // Multi-class domain model files are intentional.
                "ktlint_standard_filename" to "disabled",
                // Backing property pattern in event bus flows is valid.
                "ktlint_standard_backing-property-naming" to "disabled",
                // KDoc + EOL comment ordering is project style.
                "ktlint_standard_no-consecutive-comments" to "disabled",
                // Wildcard imports are checked by Detekt; avoid double-reporting.
                "ktlint_standard_no-wildcard-imports" to "disabled",
                // Empty placeholder files are scaffolding.
                "ktlint_standard_no-empty-file" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}
