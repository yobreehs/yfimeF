import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

val gitCommitHashProvider = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    workingDir = rootProject.rootDir
}.standardOutput.asText!!

val gitCommitDateProvider = providers.exec {
    commandLine("git log -1 --format=%cd --date=format:%y%m%d".split(' '))
    workingDir = rootProject.rootDir
}.standardOutput.asText!!

private val fixedPackageName = "sul.j"

android {
    namespace = "io.github.chsbuffer.revancedxposed"

    defaultConfig {
        applicationId = fixedPackageName
        versionCode = 41
        versionName = gitCommitDateProvider.get().trim()
        buildConfigField("String", "COMMIT_HASH", "\"${gitCommitHashProvider.get().trim()}\"")
    }
    flavorDimensions += "abi"
    productFlavors {
        create("universal") {
            dimension = "abi"
        }
    }
    packaging.resources {
        excludes.addAll(
            arrayOf(
                "META-INF/**", "**.bin"
            )
        )
    }
    buildFeatures.buildConfig = true
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xno-call-assertions"
        )
        jvmTarget = JvmTarget.JVM_17
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
//    implementation(libs.dexkit)
    implementation(group = "", name = "dexkit-android", ext = "aar")
    implementation("com.google.flatbuffers:flatbuffers-java:23.5.26") // dexkit dependency
    implementation(libs.annotation)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.jadx.core)
    testImplementation(libs.slf4j.simple)
    compileOnly(libs.xposed)
    compileOnly(project(":stub"))
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.packaging.resources.excludes.add("kotlin/**")
    }
}
