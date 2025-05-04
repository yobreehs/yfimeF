package io.github.chsbuffer.revancedxposed

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BuildConfig.DEBUG
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import kotlin.reflect.KFunction0

private typealias HookFunction = KFunction0<Unit>

class DependedHookFailedException(
    subHookName: String, exception: Throwable
) : Exception("Depended hook $subHookName failed.", exception)

@SuppressLint("CommitPrefEdits")
abstract class BaseHook(val app: Application, val lpparam: LoadPackageParam) {
    val classLoader = lpparam.classLoader!!

    // hooks
    abstract val hooks: Array<HookFunction>
    private val appliedHooks = mutableSetOf<HookFunction>()
    private val failedHooks = mutableListOf<HookFunction>()

    // cache
    private val moduleRel = BuildConfig.VERSION_CODE
    private var pref = app.getSharedPreferences("xprevanced", Context.MODE_PRIVATE)!!
    private val map = mutableMapOf<String, String>()
    private lateinit var dexkit: DexKitBridge
    private var isCached: Boolean = false

    fun Hook() {
        tryLoadCache()
        try {
            applyHooks()
            handleResult()
            logDebugInfo()
        } finally {
            closeDexKit()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun tryLoadCache() {
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)

        val id = "${packageInfo.lastUpdateTime}-$moduleRel"
        val cachedId = pref.getString("id", null)
        isCached = cachedId.equals(id)

        Logger.printInfo { "cache ID: $id" }
        Logger.printInfo { "cached ID: ${cachedId ?: ""}" }
        Logger.printInfo { "Using cached keys: $isCached" }

        if (isCached) {
            map.putAll(pref.all as Map<String, String>)
        } else {
            map["id"] = id
            createDexKit()
        }
    }

    private fun createDexKit() {
        System.loadLibrary("dexkit")
        dexkit = DexKitBridge.create(lpparam.appInfo.sourceDir)
    }

    private fun closeDexKit() {
        if (::dexkit.isInitialized) {
            dexkit.close()
        }
    }

    private fun applyHooks() {
        hooks.forEach { hook ->
            runCatching(hook).onFailure { err ->
                XposedBridge.log(err)
                failedHooks.add(hook)
            }.onSuccess {
                appliedHooks.add(hook)
            }
        }
    }

    private fun handleResult() {
        val success = failedHooks.isEmpty()
        if (!success) {
            XposedBridge.log("${lpparam.appInfo.packageName} version: ${getAppVersion()}")
            Utils.showToastLong("Error while apply following Hooks:\n${failedHooks.joinToString { it.name }}")

            clearCache()
        } else {
            saveCache()
        }
    }

    private fun logDebugInfo() {
        val success = failedHooks.isEmpty()
        if (DEBUG) {
            XposedBridge.log("${lpparam.appInfo.packageName} version: ${getAppVersion()}")
            if (success) {
                Utils.showToastLong("apply hooks success")
            }
            if (success && isCached) {
                map.forEach { key, value -> Logger.printDebug { "$key Matches: $value" } }
            }
        }
    }

    private fun getAppVersion(): String {
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
        val versionName = packageInfo.versionName
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") packageInfo.versionCode
        }
        return "$versionName ($versionCode)"
    }

    private fun saveCache() {
        if (isCached) return

        val edit = pref.edit()
        map.forEach { k, v ->
            edit.putString(k, v)
        }
        edit.commit()
    }

    private fun clearCache() {
        Logger.printInfo { "clear cache" }
        pref.edit().clear().commit()
        isCached = false
        map.clear()
    }

    fun dependsOn(vararg hooks: HookFunction) {
        hooks.forEach { hook ->
            if (appliedHooks.contains(hook)) return@forEach
            runCatching(hook).onFailure { err ->
                throw DependedHookFailedException(hook.name, err)
            }.onSuccess {
                appliedHooks.add(hook)
            }
        }
    }

    fun getDexClass(key: String, f: DexKitBridge.() -> ClassData): DexClass {
        return map[key]?.let { DexClass(it) } ?: f(dexkit).apply {
            map[key] = descriptor
            Logger.printInfo { "$key Matches: ${toDexType()}" }
        }.toDexType()
    }

    fun getDexMethod(key: String, f: DexKitBridge.() -> MethodData): DexMethod {
        return map[key]?.let { DexMethod(it) } ?: f(dexkit).apply {
            map[key] = descriptor
            Logger.printInfo { "$key Matches: ${toDexMethod()}" }
        }.toDexMethod()
    }

    fun getDexField(key: String, f: DexKitBridge.() -> FieldData): DexField {
        return map[key]?.let { DexField(it) } ?: f(dexkit).apply {
            map[key] = descriptor
            Logger.printInfo { "$key Matches: ${toDexField()}" }
        }.toDexField()
    }

    fun getString(key: String, f: DexKitBridge.() -> String): String {
        return map[key] ?: f(dexkit).also {
            map[key] = it
            Logger.printInfo { "$key Matches: $it" }
        }
    }

    fun getNumber(key: String, f: DexKitBridge.() -> Int): Int {
        return map[key]?.let { return Integer.parseInt(it) } ?: f(dexkit).also {
            map[key] = it.toString()
            Logger.printInfo { "$key Matches: $it" }
        }
    }

    fun getDexMethod(key: String): DexMethod {
        return DexMethod(map[key]!!)
    }

    fun getDexClass(key: String): DexClass {
        return DexClass(map[key]!!)
    }

    fun getDexField(key: String): DexField {
        return DexField(map[key]!!)
    }

    fun getString(key: String): String {
        return map[key]!!
    }

    fun DexMethod.hookMethod(callback: XC_MethodHook) {
        if (isMethod) {
            XposedBridge.hookMethod(getMethodInstance(lpparam.classLoader), callback)
        } else {
            XposedBridge.hookMethod(getConstructorInstance(lpparam.classLoader), callback)
        }
    }

}