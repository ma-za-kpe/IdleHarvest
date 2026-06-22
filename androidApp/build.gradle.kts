import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.googleServices)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("${rootProject.projectDir}/config/detekt/detekt.yml")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.maku.idleharvest"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.maku.idleharvest"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
        create("beta") {
            initWith(getByName("debug"))
            isDebuggable = true
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.register("buildBetaApk") {
    group = "distribution"
    description = "Builds a debug-signed APK for beta tester distribution and copies it to dist/"
    dependsOn("assembleBeta")
    doLast {
        val apkDir =
            layout.buildDirectory
                .dir("outputs/apk/beta")
                .get()
                .asFile
        val apk =
            apkDir.listFiles()?.firstOrNull { it.extension == "apk" }
                ?: error("No APK found in ${apkDir.absolutePath}")
        val dest =
            rootProject.layout.projectDirectory
                .dir("dist")
                .asFile
        dest.mkdirs()
        val destFile = File(dest, "IdleHarvest-beta-${android.defaultConfig.versionName}.apk")
        apk.copyTo(destFile, overwrite = true)
        println("Beta APK ready: ${destFile.absolutePath}")
    }
}
