plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "io.github.chsbuffer.revancedxposed"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.chsbuffer.revancedxposed"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.$versionCode"
    }
    flavorDimensions += "abi"
    productFlavors {
        create("x86_64") {
            dimension = "abi"
            ndk {
                abiFilters.add("x86_64")
            }
        }
        create("arm64_v8a") {
            dimension = "abi"
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
        create("universal") {
            dimension = "abi"
        }
    }

    packagingOptions.resources {
        excludes.addAll(
            arrayOf(
                "META-INF/**", "kotlin/**", "**.bin"
            )
        )
    }
    buildFeatures.buildConfig = true
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        compileOptions {
            freeCompilerArgs = listOf(
                "-Xno-param-assertions", "-Xno-receiver-assertions", "-Xno-call-assertions"
            )
        }
    }
}

dependencies {
    implementation(libs.dexkit)
    implementation(libs.annotation)
    compileOnly(libs.xposed)
}