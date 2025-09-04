package io.github.chsbuffer.revancedxposed

import org.luckypray.dexkit.DexKitBridge

object TestSetup {
    var lastApkPath = ThreadLocal<String>()
    val dexkit = ThreadLocal<DexKitBridge>()

    init {
        resourceMappings = ResourceFinderSafe
    }

    @JvmStatic
    fun getDexKit(apkPath: String): DexKitBridge {
        if (lastApkPath.get() == apkPath)
            return dexkit.get()!!

        try {
            System.loadLibrary("dexkit")
        } catch (_: UnsatisfiedLinkError) {
            System.loadLibrary("libdexkit")
        }

        ResourceFinderSafe.setThreadLocal(apkPath)
        dexkit.set(DexKitBridge.create(apkPath))
        return dexkit.get()!!
    }

    object ResourceFinderSafe : ResourceFinder {
        val underlying = ThreadLocal<ResourceReader>()

        fun setThreadLocal(apkPath: String) {
            if (underlying.get() == null || lastApkPath.get() != apkPath){
                val reader = ResourceReader(apkPath)
                underlying.set(reader)
            }
        }

        override operator fun get(type: String, name: String): Int = underlying.get()!![type, name]
    }
}