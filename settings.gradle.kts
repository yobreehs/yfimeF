pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://api.xposed.info")
    }
}


plugins {
    id("com.android.settings") version("8.10.0")
}

android {
    compileSdk = 35
    targetSdk = 35
    minSdk = 27
}

rootProject.name = "Revanced Xposed"
include(":app")
include(":stub")
