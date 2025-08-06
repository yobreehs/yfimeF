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

class XHookContext(val param: MethodHookParam, val outerParam: MethodHookParam)

data class XHook(
    val before: (XHookContext.() -> Unit)?,
    val after: (XHookContext.() -> Unit)?
)

class XHookBuilder {
    private var before: (XHookContext.() -> Unit)? = null
    private var after: (XHookContext.() -> Unit)? = null

    fun before(f: XHookContext.() -> Unit) {
        this.before = f
    }

    fun after(f: XHookContext.() -> Unit) {
        this.after = f
    }

    fun replace(f: XHookContext.() -> Any) {
        before = {
            runCatching {
                param.result = f()
            }.onFailure { err ->
                param.throwable = err
            }
        }
        after = null
    }

    fun returnConstant(obj: Any) {
        replace { obj }
    }

    fun build() = XHook(before, after)
}

class ScopedHook : XC_MethodHook {
    constructor(hookMethod: Member, f: XHookBuilder.() -> Unit) : this(
        hookMethod, XHookBuilder().apply(f).build()
    )

    constructor(hookMethod: Member, hook: XHook) {
        XposedBridge.hookMethod(hookMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val outerParam = outerParam.get()
                if (outerParam == null) return
                hook.before?.invoke(XHookContext(param, outerParam))
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val outerParam = outerParam.get()
                if (outerParam == null) return
                hook.after?.invoke(XHookContext(param, outerParam))
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

            throw ClassNotFoundException(name);
        }
    })
}