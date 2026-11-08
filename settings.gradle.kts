pluginManagement {
    includeBuild("node_modules/@react-native/gradle-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
    id("com.facebook.react.settings")
}

extensions.configure<com.facebook.react.ReactSettingsExtension> {
    autolinkLibrariesFromCommand(
        workingDirectory = settingsDir,
        lockFiles = files("package.json", "package-lock.json", "react-native.config.js"),
    )
}

rootProject.name = "FccClaude"
include(":app")
includeBuild("node_modules/@react-native/gradle-plugin")
