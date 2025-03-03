package io.github.chsbuffer.revancedxposed

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import app.revanced.extension.shared.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import kotlin.reflect.KFunction0

class DependedHookFailedException(
    subHookName: String, exception: Throwable
) : Exception("Depended hook $subHookName failed.", exception)

@SuppressLint("CommitPrefEdits")
abstract class BaseHook(val app: Application, val lpparam: LoadPackageParam) {
    val classLoader = lpparam.classLoader
    // hooks
    abstract val hooks: Array<KFunction0<Unit>>
    private val appliedHooks: MutableSet<KFunction0<Unit>> = mutableSetOf()
    private val failedHook = mutableListOf<KFunction0<Unit>>()

    // cache
    private val moduleRel = BuildConfig.VERSION_CODE
    private lateinit var pref: SharedPreferences
    private lateinit var map: MutableMap<String, String>
    lateinit var dexkit: DexKitBridge
    private var cached: Boolean = false

    fun Hook() {
        loadCache()

        if (!cached) dexkit = createDexKit(lpparam)

        val success = applyHooks(app, *hooks)

        if (success) saveCache()
        else clearCache()

        if (!cached) dexkit.close()
    }

    fun dependsOn(vararg hooks: KFunction0<Unit>) {
        hooks.forEach { hook ->
            if (appliedHooks.contains(hook)) return@forEach
            runCatching(hook).onFailure { err ->
                XposedBridge.log(err)
                throw DependedHookFailedException(hook.name, err)
            }
        }
    }

    private fun createDexKit(lpparam: LoadPackageParam): DexKitBridge {
        System.loadLibrary("dexkit")
        return DexKitBridge.create(lpparam.appInfo.sourceDir)
    }

    private fun applyHooks(app: Application, vararg hooks: KFunction0<Unit>): Boolean {
        hooks.forEach { hook ->
            runCatching(hook).onFailure { err ->
                XposedBridge.log(err)
                failedHook.add(hook)
            }
        }

        val isAllSuccess = failedHook.isEmpty()
        if (isAllSuccess) {
            if (BuildConfig.DEBUG) Toast.makeText(
                app, "apply hooks success", Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                app,
                "Error while apply following Hooks:\n${failedHook.joinToString(", ") { it.name }}",
                Toast.LENGTH_LONG
            ).show()
        }
        if (BuildConfig.DEBUG or !isAllSuccess) XposedBridge.log("${lpparam.appInfo.packageName} version: ${getAppVersion()}")

        return isAllSuccess
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

    @Suppress("UNCHECKED_CAST")
    private fun loadCache() {
        pref = app.getSharedPreferences("xprevanced", Context.MODE_PRIVATE)
        val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)

        val id = "${packageInfo.lastUpdateTime}-$moduleRel"
        val cachedId = pref.getString("id", null)
        cached = cachedId.equals(id)

        Logger.printInfo { "cache ID: $id" }
        Logger.printInfo { "cached ID: ${cachedId ?: ""}" }
        Logger.printInfo { "Using cached keys: $cached" }

        map = if (cached) {
            pref.all.toMutableMap() as MutableMap<String, String>
        } else {
            mutableMapOf("id" to id)
        }
    }

    private fun saveCache() {
        val edit = pref.edit()
        map.forEach { k, v ->
            edit.putString(k, v)
        }
        edit.commit()
    }

    private fun clearCache() {
        pref.edit().clear().apply()
    }

    fun getDexClass(key: String, f: () -> ClassData): DexClass {
        return map[key]?.let { DexClass(it) } ?: f().apply {
            map[key] = descriptor
            Logger.printInfo { "$key Matches: ${toDexType()}" }
        }.toDexType()
    }

    fun getDexMethod(key: String, f: () -> MethodData): DexMethod {
        return map[key]?.let { DexMethod(it) } ?: f().apply {
            map[key] = descriptor
            Logger.printInfo { "$key Matches: ${toDexMethod()}" }
        }.toDexMethod()
    }

    fun getDexField(key: String, f: () -> FieldData): DexField {
        return map[key]?.let { DexField(it) } ?: f().apply {
            map[key] = descriptor
            Logger.printInfo { "$key Matches: ${toDexField()}" }
        }.toDexField()
    }

    fun getString(key: String, f: () -> String): String {
        return map[key] ?: f().also {
            map[key] = it
            Logger.printInfo { "$key Matches: $it" }
        }
    }

    fun getNumber(key: String, f: () -> Number): Number {
        return map[key]?.let { return Integer.parseInt(it) } ?: f().also {
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

    fun setString(key: String, value: Any) {
        val str = when (value) {
            is ClassData -> value.toDexType()
            is MethodData -> value.toDexMethod()
            is FieldData -> value.toDexField()
            else -> value
        }
        Logger.printInfo { "$key Matches: $str" }
        map[key] = str.toString()
    }

    fun setString(key: String, func: () -> Any) {
        setString(key, func())
    }

    fun DexMethod.hookMethod(callback: XC_MethodHook) {
        XposedBridge.hookMethod(getMethodInstance(lpparam.classLoader), callback)
    }

    fun DexMethod.hookConstructorInstance(callback: XC_MethodHook) {
        XposedBridge.hookMethod(getConstructorInstance(lpparam.classLoader), callback)
    }
}