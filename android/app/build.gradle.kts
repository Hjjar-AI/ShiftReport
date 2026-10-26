import com.android.build.api.artifact.SingleArtifact
import java.io.StringReader
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersionProperties = Properties().apply {
    load(StringReader(providers.fileContents(
        rootProject.layout.projectDirectory.file("gradle/app-version.properties")
    ).asText.get()))
}
val currentAppVersionCode = providers.gradleProperty("shiftreport.versionCode")
    .orElse(appVersionProperties.getProperty("versionCode")).get().toInt()
val currentAppVersionName = providers.gradleProperty("shiftreport.versionName")
    .orElse(appVersionProperties.getProperty("versionName")).get()
require(currentAppVersionCode in 1..2_100_000_000) { "Invalid app versionCode" }
require(currentAppVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)*"))) { "Invalid app versionName" }

// Local file and CI environment inputs only; signing credentials never enter source.
val releaseSigningProperties = Properties().apply {
    load(StringReader(providers.fileContents(
        rootProject.layout.projectDirectory.file("keystore.properties")
    ).asText.orElse("").get()))
}
fun releaseSigningValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }
        ?: releaseSigningProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

// Shared development identity is selected locally; writable caches remain account-specific.
val debugSigningProperties = Properties().apply {
    load(StringReader(providers.fileContents(
        rootProject.layout.projectDirectory.file("local.properties")
    ).asText.orElse("").get()))
}
val configuredDebugKeystorePath = providers.environmentVariable("SHIFTREPORT_DEBUG_KEYSTORE")
    .orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: debugSigningProperties.getProperty("debug.keystore")?.trim()?.takeIf { it.isNotEmpty() }
val configuredDebugKeystore = configuredDebugKeystorePath?.let { path ->
    rootProject.file(path).also { key ->
        require(key.isFile && key.canRead()) {
            "Configured debug keystore is missing or unreadable; check debug.keystore / SHIFTREPORT_DEBUG_KEYSTORE."
        }
    }
}

android {
    namespace = "com.hos.rushdpatients"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.2"
    }

    defaultConfig {
        applicationId = "com.hos.rushdpatients"
        minSdk = 23
        targetSdk = 34
        versionCode = currentAppVersionCode
        versionName = currentAppVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        getByName("debug") {
            configuredDebugKeystore?.let { storeFile = it }
        }
        create("release") {
            storeFile = releaseSigningValue("SHIFTREPORT_KEYSTORE_FILE", "storeFile")?.let { rootProject.file(it) }
            storePassword = releaseSigningValue("SHIFTREPORT_KEYSTORE_PASSWORD", "storePassword")
            keyAlias = releaseSigningValue("SHIFTREPORT_KEY_ALIAS", "keyAlias")
            keyPassword = releaseSigningValue("SHIFTREPORT_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        debug {
            if (providers.gradleProperty("shiftreport.isolatedTests").orNull == "true") {
                applicationIdSuffix = ".verification"
            }
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Core / Compose
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.window:window:1.2.0")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Security
    implementation("androidx.security:security-crypto:1.0.0")
    // Pinned for this project's AGP 8.5, Kotlin 1.9, and compileSdk 34 toolchain.
    implementation(group = "net.zetetic", name = "sqlcipher-android", version = "4.10.0")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Reliable background synchronization
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


// Export only packaged APK artifacts. Failed compilation/packaging prevents the
// copy dependency from running; debug/release and split names remain distinct.
val apkExportDirectory = providers.gradleProperty("shiftreport.apkExportDir")
    .orElse(providers.systemProperty("user.home").map { "$it/Desktop/ShiftReport" })
val apkExportVersion = android.defaultConfig.versionName.orEmpty()
    .replace(Regex("[^A-Za-z0-9._-]"), "_")

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name
        val variantTaskName = variantName.replaceFirstChar { it.uppercaseChar() }
        val exportApk = tasks.register<Copy>("export${variantTaskName}Apk") {
            group = "build"
            description = "Copy $variantName APKs to the user's Desktop/ShiftReport folder."
            from(variant.artifacts.get(SingleArtifact.APK))
            include("*.apk")
            into("${apkExportDirectory.get()}/$variantName")
            // Built-in regex mapping stores strings, not a script-capturing lambda.
            rename("^(?:app-)?(.+\\.apk)$", "ShiftReport-v$apkExportVersion-\$1")
        }
        tasks.matching { it.name == "assemble$variantTaskName" }.configureEach {
            dependsOn(exportApk)
        }
    }
}
