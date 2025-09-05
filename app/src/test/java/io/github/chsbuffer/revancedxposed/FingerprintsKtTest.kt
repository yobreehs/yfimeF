package io.github.chsbuffer.revancedxposed

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.luckypray.dexkit.DexKitBridge
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.time.measureTime

@ParameterizedClass
@ArgumentsSource(FilePathArgumentsProvider::class)
class FingerprintsKtTest(val apkPath: Path) {

    val dexkit: DexKitBridge = TestSetup.getDexKit(apkPath.toString())

    fun testFingerprints(clazz: Class<*>) {
        clazz.methods.forEach { method ->
            if (method.isAnnotationPresent(SkipTest::class.java)) return@forEach
            if (!method.isStatic) return@forEach

            val func = method(null) as? (DexKitBridge) -> Any ?: return@forEach
            print("${method.name.drop(3)}: ")
            assertDoesNotThrow {
                measureTime {
                    val value = func(dexkit)
                    if (value is List<*>) {
                        assertTrue(value.isNotEmpty())
                        print(value.joinToString(", "))
                    } else {
                        print("$value")
                    }
                }.let {
                    if (it.inWholeMilliseconds > 20)
                        println(", slow match: $it")
                    else
                        println()
                }
            }
        }
    }

    @TestFactory
    fun fingerprintTest(): Iterator<DynamicTest> = sequence {
        val app = when {
            apkPath.name.startsWith("com.google.android.youtube") -> "youtube"
            apkPath.name.startsWith("com.google.android.apps.youtube.music") -> "music"
            apkPath.name.startsWith("com.spotify.music") -> "spotify"
            apkPath.name.startsWith("com.reddit.frontpage") -> "reddit"
            else -> return@sequence
        }

        val classLoader = Thread.currentThread().contextClassLoader!!

        val fingerprintClassList =
            Files.walk(Path("src/main/java/io/github/chsbuffer/revancedxposed/$app"))
                .filter { Files.isRegularFile(it) && it.fileName.toString() == "Fingerprints.kt" }
                .map {
                    // drop src/main/java
                    it.invariantSeparatorsPathString.split("/").drop(3)
                        // convert to class name
                        .joinToString(".").replace(".kt", "Kt")
                }.toList()

        fingerprintClassList.forEach {
            val clz = classLoader.loadClass(it)
            val category = it.split(".").drop(5).joinToString(".") // drop your.patches.app prefix
            yield(DynamicTest.dynamicTest(category) { testFingerprints(clz) })
        }
    }.iterator()
}