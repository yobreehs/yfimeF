package io.github.chsbuffer.revancedxposed

import android.annotation.SuppressLint
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

typealias IScopedHookCallback = ScopedHookParam.(MethodHookParam) -> Unit
typealias IHookCallback = (MethodHookParam) -> Unit

class HookDsl<TCallback>(emptyCallback: TCallback) {
    var before: TCallback = emptyCallback
    var after: TCallback = emptyCallback

    fun before(f: TCallback) {
        this.before = f
    }

    fun after(f: TCallback) {
        this.after = f
    }
}

inline fun Member.hookMethod(crossinline block: HookDsl<IHookCallback>.() -> Unit) {
    val builder = HookDsl<IHookCallback> {}.apply(block)
    hookMethodInternal(builder.before, builder.after)
}

inline fun Member.hookMethodInternal(
    crossinline before: IHookCallback, crossinline after: IHookCallback
) {
    XposedBridge.hookMethod(this, object : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            before(param)
        }

        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
            after(param)
        }
    })
}

@JvmInline
value class ScopedHookParam(val outerParam: MethodHookParam)

fun scopedHook(vararg pairs: Pair<Member, HookDsl<IScopedHookCallback>.() -> Unit>): XC_MethodHook {
    val hook = ScopedHook()
    pairs.forEach { (member, block) ->
        val builder = HookDsl<IScopedHookCallback> {}.apply(block)
        hook.hookInnerMethod(member, builder.before, builder.after)
    }
    return hook
}

inline fun scopedHook(
    hookMethod: Member, crossinline f: HookDsl<IScopedHookCallback>.() -> Unit
): XC_MethodHook {
    val hook = ScopedHook()
    val builder = HookDsl<IScopedHookCallback> {}.apply(f)
    hook.hookInnerMethod(hookMethod, builder.before, builder.after)
    return hook
}

class ScopedHook : XC_MethodHook() {
    inline fun hookInnerMethod(
        hookMethod: Member,
        crossinline before: IScopedHookCallback,
        crossinline after: IScopedHookCallback
    ) {
        XposedBridge.hookMethod(hookMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val outerParam = outerParam.get()
                if (outerParam == null) return
                before(ScopedHookParam(outerParam), param)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val outerParam = outerParam.get()
                if (outerParam == null) return
                after(ScopedHookParam(outerParam), param)
            }
        })
    }

    val outerParam: ThreadLocal<XC_MethodHook.MethodHookParam> = ThreadLocal<MethodHookParam>()

    override fun beforeHookedMethod(param: MethodHookParam) {
        outerParam.set(param)
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        outerParam.remove()
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


@SuppressLint("DiscouragedPrivateApi")
fun injectHostClassLoaderToSelf(self: ClassLoader, classLoader: ClassLoader) {
    val loader = self.parent
    val host = classLoader
    val bootClassLoader = Context::class.java.classLoader!!

    self.setObjectField("parent", object : ClassLoader(bootClassLoader) {
        override fun findClass(name: String?): Class<*> {
            try {
                return bootClassLoader.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }

            try {
                return loader.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
            try {
                return host.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }

            throw ClassNotFoundException(name)
        }
    })
}

@Suppress("UNCHECKED_CAST")
fun Class<*>.enumValueOf(name: String): Enum<*>? {
    return try {
        java.lang.Enum.valueOf(this as Class<out Enum<*>>, name)
    } catch (_: IllegalArgumentException) {
        null
    }
}