package io.github.chsbuffer.revancedxposed.music.layout.upgradebutton

import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.findFieldDirect
import io.github.chsbuffer.revancedxposed.music.MusicHook

val pivotBarElementField = findFieldDirect {
    pivotBarConstructorFingerprint().declaredClass!!.fields.single { f -> f.typeName == "java.util.List" }
}

fun MusicHook.RemoveUpgradeButton() {
    ::pivotBarConstructorFingerprint.hookMethod {
        val pivotBarElementField = ::pivotBarElementField.field

        after { param ->
            val list = pivotBarElementField.get(param.thisObject)
            try {
                XposedHelpers.callMethod(list, "remove", 4)
            } catch (e: XposedHelpers.InvocationTargetError) {
                if (e.cause !is IndexOutOfBoundsException) throw e
            }
        }
    }
}