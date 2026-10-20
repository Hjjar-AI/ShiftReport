plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("com.google.devtools.ksp") apply false
    id("com.google.dagger.hilt.android") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

// The POSIX launcher supplies an account-local output root for shared checkouts.
// IDEs and callers without this property retain the standard build directories.
providers.gradleProperty("shiftreport.buildRoot").orNull?.let { accountBuildRoot ->
    allprojects {
        val outputPath = if (path == ":") "root" else path.removePrefix(":").replace(':', '/')
        layout.buildDirectory.set(rootProject.file(accountBuildRoot).resolve(outputPath))
    }
}
