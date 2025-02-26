package io.github.chsbuffer.revancedxposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Member

class ScopedHook(val hookMethod: Member, val callback: XC_MethodHook) : XC_MethodHook() {
    lateinit var Unhook: XC_MethodHook.Unhook
    override fun beforeHookedMethod(param: MethodHookParam?) {
        Unhook = XposedBridge.hookMethod(hookMethod, callback)
    }

    override fun afterHookedMethod(param: MethodHookParam?) {
        Unhook.unhook()
    }
}

fun MethodMatcher.strings(vararg strings: String) {
    this.usingStrings(strings.toList())
}