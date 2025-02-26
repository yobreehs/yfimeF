package io.github.chsbuffer.revancedxposed

import android.app.Application
import app.revanced.extension.shared.Logger
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.music.MusicHook
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import kotlin.system.measureTimeMillis

class MainHook : IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {
    lateinit var startupParam: IXposedHookZygoteInit.StartupParam
    lateinit var resparam: XC_InitPackageResources.InitPackageResourcesParam

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        when (lpparam.packageName) {
            "com.google.android.apps.youtube.music" -> {
                inContext(lpparam) { app ->
                    val t = measureTimeMillis {
                        MusicHook(app, lpparam).Hook()
                    }
                    Logger.printDebug { "Youtube Music handleLoadPackage: ${t}ms" }
                }
            }

            "com.google.android.youtube" -> {
                inContext(lpparam) { app ->
                    val t = measureTimeMillis {
                        YoutubeHook(app, lpparam, resparam, startupParam).Hook()
                    }
                    Logger.printDebug { "Youtube handleLoadPackage: ${t}ms" }
                }
            }
        }
    }

    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        this.resparam = resparam
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        this.startupParam = startupParam
    }
}

fun inContext(lpparam: LoadPackageParam, f: (Application) -> Unit) {
    val appClazz = XposedHelpers.findClass(lpparam.appInfo.className, lpparam.classLoader)
    XposedBridge.hookMethod(appClazz.getMethod("onCreate"), object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val app = param.thisObject as Application
            f(app)
        }
    })
}
