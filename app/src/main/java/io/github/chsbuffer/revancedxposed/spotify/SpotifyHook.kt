package io.github.chsbuffer.revancedxposed.spotify

import android.app.Application
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.spotify.misc.UnlockPremiumPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.ScopedHookSafe
import io.github.chsbuffer.revancedxposed.strings
import org.luckypray.dexkit.query.enums.StringMatchType

class SpotifyHook(app: Application, lpparam: LoadPackageParam) : BaseHook(app, lpparam) {
    override val hooks = arrayOf(
        ::UnlockPremium
    )

    fun UnlockPremium() {
        // Logger
        Utils.setContext(app)

        // Override the attributes map in the getter method.
        getDexMethod("productStateProtoFingerprint") {
            dexkit.findClass {
                matcher {
                    className("ProductStateProto", StringMatchType.EndsWith)
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
                Logger.printDebug { field.get(param.thisObject)!!.toString() }
                UnlockPremiumPatch.overrideAttribute(field.get(param.thisObject) as Map<*, *>)
            }
        })

        // Add the query parameter trackRows to show popular tracks in the artist page.
        getDexMethod("buildQueryParametersFingerprint") {
            dexkit.findMethod {
                matcher {
                    strings("trackRows", "device_type:tablet")
                }
            }.single()
        }.hookMethod(object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val result = param.result
                val FIELD = "checkDeviceCapability"
                if (result.toString().contains("${FIELD}=")) {
                    param.result = XposedBridge.invokeOriginalMethod(
                        param.method, param.thisObject, arrayOf(param.args[0], true)
                    )
                }
            }
        })

        // Disable the "Spotify Premium" upsell experiment in context menus.
        val contextMenuExperiments = getDexMethod("contextMenuExperimentsFingerprint") {
            dexkit.findMethod {
                matcher {
                    paramCount = 1
                    strings("remove_ads_upsell_enabled")
                }
            }.single().also { method ->
                getDexMethod("checkExperiments") {
                    method.invokes.distinct().single { it.returnTypeName == "boolean" }
                }
            }
        }
        val checkExperimentsMethod = getDexMethod("checkExperiments").getMethodInstance(classLoader)
        contextMenuExperiments.hookMethod(ScopedHookSafe(checkExperimentsMethod) {
            returnConstant(false)
        })
    }
}