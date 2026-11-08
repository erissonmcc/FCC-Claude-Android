import com.facebook.react.tasks.BundleHermesCTask

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.facebook.react")
}

val reactNativeDevSupport = providers.gradleProperty("reactNativeDevSupport").orNull?.toBoolean() == true

// Hermes can execute plain JS; its distributed bytecode compiler is x86_64-only
// on Linux, so Termux builds keep the Metro bundle as JavaScript.
if (System.getenv("FCC_COMPILE_HERMES_BYTECODE") == "false") {
    gradle.projectsEvaluated {
        tasks.withType<BundleHermesCTask>().configureEach {
            hermesEnabled.set(false)
            minifyEnabled.set(true)
        }
    }
}

react {
	root = file("..")
	cliFile = file("../node_modules/react-native/cli.js")
	reactNativeDir = file("../node_modules/react-native")
    debuggableVariants.set(if (reactNativeDevSupport) listOf("debug") else emptyList())
    autolinkLibrariesWithApp()
}

android {
    namespace = "com.fcc.claude"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fcc.claude"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "REACT_NATIVE_DEV_SUPPORT", reactNativeDevSupport.toString())

        providers.gradleProperty("reactNativeArchitectures").orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { architectures -> ndk.abiFilters.addAll(architectures.split(",")) }
    }

    buildFeatures { buildConfig = true }

    if (System.getenv("PREFIX")?.contains("/com.termux/files/usr") == true) {
        externalNativeBuild.cmake.version = "4.4.4"
        defaultConfig.externalNativeBuild.cmake.arguments.add(
            "-DCMAKE_TOOLCHAIN_FILE=${rootProject.file("scripts/termux-ndk-arm64-toolchain.cmake").absolutePath}"
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.compose.runtime:runtime:1.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    if (providers.gradleProperty("hermesEnabled").orNull?.toBoolean() == true) {
        implementation("com.facebook.react:hermes-android")
    }
    implementation("com.facebook.react:react-android")
    testImplementation("junit:junit:4.13.2")
}
