package io.github.chsbuffer.revancedxposed.meta

import android.app.Application
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.strings
import java.lang.reflect.Modifier

class MetaHook(app: Application, lpparam: XC_LoadPackage.LoadPackageParam) :
    BaseHook(app, lpparam) {
    override val hooks = arrayOf(::HideAds)

    fun HideAds() {
        getDexMethod("adInjectorFingerprint") {
            findMethod {
                matcher {
                    modifiers = Modifier.PRIVATE
                    returnType = "void"
                    strings(
                        "SponsoredContentController.processValidatedContent",
                    )
                }
            }.single()
        }.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }
}