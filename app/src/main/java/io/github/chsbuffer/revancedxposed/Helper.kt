package io.github.chsbuffer.revancedxposed

import android.content.Context
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.lang.reflect.Member

typealias XFunc = (param: MethodHookParam, outerParam: MethodHookParam) -> Unit

class XFuncBuilder {
    data class XFuncHolder(val before: XFunc?, val after: XFunc?)

    private var before: XFunc? = null
    private var after: XFunc? = null

    fun before(f: XFunc) {
        this.before = f
    }

    fun after(f: XFunc) {
        this.after = f
    }

    fun replace(f: (param: MethodHookParam) -> Any) {
        before = { param, _ ->
            runCatching {
                param.result = f(param)
            }.onFailure { err ->
                param.throwable = err
            }
        }
        after = null
    }

    fun returnConstant(obj: Any) {
        replace { obj }
    }

    fun build() = XFuncHolder(before, after)
}

class ScopedHook : XC_MethodHook {
    constructor(hookMethod: Member, f: XFuncBuilder.() -> Unit) : this(
        hookMethod, XFuncBuilder().apply(f).build()
    )

    constructor(hookMethod: Member, hook: XFuncBuilder.XFuncHolder) {
        XposedBridge.hookMethod(hookMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (lock.get() != true) return
                hook.before?.invoke(param, outerParam.get()!!)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (lock.get() != true) return
                hook.after?.invoke(param, outerParam.get()!!)
            }
        })
    }

    val lock: ThreadLocal<Boolean> = ThreadLocal<Boolean>()
    val outerParam: ThreadLocal<XC_MethodHook.MethodHookParam> = ThreadLocal<MethodHookParam>()

    override fun beforeHookedMethod(param: MethodHookParam) {
        outerParam.set(param)
        lock.set(true)
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        lock.set(false)
    }
}

lateinit var XposedInit: IXposedHookZygoteInit.StartupParam

private val resourceLoader by lazy @RequiresApi(Build.VERSION_CODES.R) {
    val fileDescriptor = ParcelFileDescriptor.open(
        File(XposedInit.modulePath), ParcelFileDescriptor.MODE_READ_ONLY
    )
    val provider = ResourcesProvider.loadFromApk(fileDescriptor)
    val loader = ResourcesLoader()
    loader.addProvider(provider)
    return@lazy loader
}

fun Context.addModuleAssets() {
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//        resources.addLoaders(resourceLoader)
//        return
//    }

    resources.assets.callMethod("addAssetPath", XposedInit.modulePath)
}