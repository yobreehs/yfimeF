import groovy.xml.MarkupBuilder
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.NodeChild

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "io.github.chsbuffer.revancedxposed"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.chsbuffer.revancedxposed"
        minSdk = 27
        targetSdk = 35
        versionCode = 8
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
