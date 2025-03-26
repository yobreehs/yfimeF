package io.github.chsbuffer.revancedxposed.spotify

import android.app.Application
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.spotify.misc.UnlockPremiumPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BaseHook
import org.luckypray.dexkit.query.enums.StringMatchType

class SpotifyHook(app: Application, lpparam: LoadPackageParam) : BaseHook(app, lpparam) {
    override val hooks = arrayOf(
        ::UnlockPremium
    )

    fun UnlockPremium() {
        Utils.setContext(app)
        getDexMethod("productStateProtoFingerprint") {
            dexkit.findClass {
                matcher {
                    className("ProductStateProto$", StringMatchType.SimilarRegex)
                }
            }.findMethod {
                matcher {
                    returnType = "java.util.Map"
                }
            }.single().also { method ->
                getDexField("attributesMapField") {
                    method.usingFields.single().field
                }
            }
        }.hookMethod(object : XC_MethodHook() {
            val field = getDexField("attributesMapField").getFieldInstance(classLoader)
            override fun beforeHookedMethod(param: MethodHookParam) {
                Logger.printDebug { field.get(param.thisObject).toString() }
                UnlockPremiumPatch.overrideAttribute(field.get(param.thisObject) as Map<*, *>)
            }
        })
    }
}