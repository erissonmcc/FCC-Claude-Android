import com.android.build.api.variant.LibraryAndroidComponentsExtension

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("com.facebook.react.rootproject")
}

extra["compileSdkVersion"] = 36
extra["minSdkVersion"] = 26
extra["targetSdkVersion"] = 35
extra["reactNativeAndroidRoot"] = file("node_modules/react-native")

subprojects {
    plugins.withId("com.android.library") {
        extensions.getByType(LibraryAndroidComponentsExtension::class.java)
            .finalizeDsl { android ->
                android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
                android.compileOptions.targetCompatibility = JavaVersion.VERSION_17
                if (name in setOf(
                        "react-native-gesture-handler",
                        "react-native-reanimated",
                        "react-native-worklets",
                        "react-native-unistyles"
                    ) &&
                    System.getenv("PREFIX")?.contains("/com.termux/files/usr") == true
                ) {
                    android.externalNativeBuild.cmake.version = "4.4.4"
                    android.defaultConfig.externalNativeBuild.cmake.arguments.add(
                        "-DCMAKE_TOOLCHAIN_FILE=${rootProject.file("scripts/termux-ndk-arm64-toolchain.cmake").absolutePath}"
                    )
                }
        }
    }
}
