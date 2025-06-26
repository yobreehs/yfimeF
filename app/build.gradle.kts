import groovy.xml.MarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.NodeChild
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "io.github.chsbuffer.revancedxposed"

    defaultConfig {
        applicationId = "io.github.chsbuffer.revancedxposed"
        versionCode = 19
        versionName = "1.0.$versionCode"
        val patchVersion = "5.28.0"
        buildConfigField("String", "PATCH_VERSION", "\"$patchVersion\"")
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
    androidResources {
        additionalParameters += arrayOf("--allow-reserved-package-id", "--package-id", "0x4b")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
}

dependencies {
    implementation(libs.dexkit)
    implementation(libs.annotation)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.fuel)
    compileOnly(libs.xposed)
}

abstract class GenerateStringsTask @Inject constructor(
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun action() {
        val inputDir = inputDirectory.get().asFile

        inputDir.listFiles()?.forEach {
            val name = it.name
            val inputFile = File(it, "strings.xml")
            val genResDir = File(outputDirectory.get().asFile, name).apply { mkdirs() }
            val outputFile = File(genResDir, "strings.xml")

            val inputXml = XmlSlurper().parse(inputFile)

            outputFile.writer().use { writer ->
                MarkupBuilder(writer).run {
                    doubleQuotes = true
                    withGroovyBuilder {
                        "resources" {
                            inputXml.children().children().children().forEach {
                                val node = it as NodeChild
                                "string"("name" to node.getProperty("@name")) { mkp.yield(it.text()) }
                            }
                        }
                    }
                }
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val task = project.tasks.register<GenerateStringsTask>("generate${variant.name}Strings") {
            val stringResourceDir = project.file("src/main/addresources")
            inputDirectory.set(stringResourceDir)
        }
        variant.sources.res?.addGeneratedSourceDirectory(
            task, GenerateStringsTask::outputDirectory
        )
    }
}
