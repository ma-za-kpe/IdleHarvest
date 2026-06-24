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
    buildFeatures {
        buildConfig = true
    }

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
        versionCode = 3
        versionName = "1.2"
        buildConfigField("String", "MODEL_ARTIFACT_BASE_URL", "\"http://127.0.0.1:8080/api/models\"")
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
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
    }
}

tasks.register("buildBetaApk") {
    group = "distribution"
    description = "Builds a debug-signed APK for beta tester distribution, copies it to dist/, and deploys via Firebase App Distribution (Gradle task entrypoint for distribution)"
    notCompatibleWithConfigurationCache("Uses Gradle script object references and an external Firebase CLI process.")
    dependsOn("assembleDebug")
    doLast {
        val apkDir =
            layout.buildDirectory
                .dir("outputs/apk/debug")
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

        // Deploy via Firebase App Distribution using CLI (integrated into the Gradle distribution task)
        // Uses the same appId from google-services.json. Runs the upload as part of this task.
        val appId = "1:447391948140:android:6e5cc46727f7ea821fa749"
        val releaseNotes = "Deployed via Gradle buildBetaApk task on ${System.currentTimeMillis()}. From senior audit run. (Debug-signed for beta)"
        val testerGroups = listOf("internal-testers")
        val testerEmails =
            (
                providers.gradleProperty("appDistributionTesters").orNull
                    ?: System.getenv("APP_DISTRIBUTION_TESTERS")
            )?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        println("Running Firebase App Distribution from Gradle task...")
        val firebaseExecutable =
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                "firebase.cmd"
            } else {
                "firebase"
            }
        val process =
            ProcessBuilder(
                buildList {
                    add(firebaseExecutable)
                    add("appdistribution:distribute")
                    add(destFile.absolutePath)
                    add("--app")
                    add(appId)
                    if (testerGroups.isNotEmpty()) {
                        add("--groups")
                        add(testerGroups.joinToString(","))
                    }
                    if (testerEmails.isNotEmpty()) {
                        add("--testers")
                        add(testerEmails.joinToString(","))
                    }
                    add("--release-notes")
                    add(releaseNotes)
                },
            ).redirectErrorStream(true)
                .start()
        if (testerEmails.isNotEmpty()) {
            println("Direct testers configured: ${testerEmails.joinToString(",")}")
        }
        process.inputStream.bufferedReader().use { reader ->
            reader.lines().forEach { println(it) }
        }
        val exit = process.waitFor()
        if (exit == 0) {
            println("Firebase App Distribution upload successful via Gradle task!")
        } else {
            println("Firebase App Distribution exited with code $exit (may need groups/tester config or auth).")
        }
    }
}
