package io.github.chsbuffer.revancedxposed.music

import android.app.Application
import android.view.View
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.InvocationTargetError
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BaseHook
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

class MusicHook(app: Application, lpparam: LoadPackageParam) : BaseHook(app, lpparam) {
    override val hooks = arrayOf(
        ::ExtensionHook,
        ::HideMusicVideoAds,
        ::MinimizedPlayback,
        ::RemoveUpgradeButton,
        ::HideGetPremium,
        ::EnableExclusiveAudioPlayback,
    )

    fun ExtensionHook() {
        Utils.setContext(app)
    }

    fun EnableExclusiveAudioPlayback() {
        getDexMethod("AllowExclusiveAudioPlaybackFingerprint") {
            dexkit.findMethod {
                matcher { addEqString("probably_has_unlimited_entitlement") }
            }.single().invokes.findMethod {
                matcher {
                    returnType = "boolean"
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    paramCount = 0
                }
            }.single()
        }.hookMethod(XC_MethodReplacement.returnConstant(true))
    }

    fun HideGetPremium() {
        // HideGetPremiumFingerprint
        getDexMethod("HideGetPremiumFingerprint") {
            dexkit.findMethod {
                matcher {
                    modifiers = Modifier.FINAL or Modifier.PUBLIC
                    usingStrings(
                        listOf("FEmusic_history", "FEmusic_offline"), StringMatchType.Equals
                    )
                }
            }.single()
        }.hookMethod(object : XC_MethodHook() {
            val id = Utils.getResourceIdentifier("unlimited_panel", "id")
            override fun afterHookedMethod(param: MethodHookParam) {
                val thiz = param.thisObject
                for (field in thiz.javaClass.fields) {
                    val view = field.get(thiz)
                    if (view !is View) continue
                    val panelView = view.findViewById<View>(id) ?: continue
                    Logger.printDebug { "hide get premium" }
                    panelView.visibility = View.GONE
                    break
                }
            }
        })
    }

    fun RemoveUpgradeButton() {
        // PivotBarConstructorFingerprint
        getDexMethod("PivotBarConstructorFingerprint") {
            dexkit.findMethod {
                matcher {
                    name = "<init>"
                    returnType = "void"
                    modifiers = Modifier.PUBLIC
                    paramTypes(null, "boolean")
                    opNames = listOf(
                        "check-cast",
                        "invoke-interface",
                        "goto",
                        "iput-object",
                        "return-void",
                    )
                }
            }.single().also { method ->
                getDexField("pivotBarElementField") {
                    method.declaredClass!!.fields.single { f -> f.typeName == "java.util.List" }
                }
            }
        }.hookMethod(object : XC_MethodHook() {
            val pivotBarElementField =
                getDexField("pivotBarElementField").getFieldInstance(classLoader)

            override fun afterHookedMethod(param: MethodHookParam) {
                val list = pivotBarElementField.get(param.thisObject)
                try {
                    XposedHelpers.callMethod(list, "remove", 4)
                } catch (e: InvocationTargetError) {
                    if (e.cause !is IndexOutOfBoundsException) throw e
                }
            }
        })
    }

    fun MinimizedPlayback() {
        getDexMethod("BackgroundPlaybackDisableFingerprint") {
            dexkit.findMethod {
                matcher {
                    returnType = "boolean"
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramCount = 1
                    opNames = listOf(
                        "const/4",
                        "if-eqz",
                        "iget",
                        "and-int/lit16",
                        "if-eqz",
                        "iget-object",
                        "if-nez",
                        "sget-object",
                        "iget",
                    )
                }
            }.single()
        }.hookMethod(XC_MethodReplacement.returnConstant(true))

        // KidsMinimizedPlaybackPolicyControllerFingerprint
        getDexMethod("KidsMinimizedPlaybackPolicyControllerFingerprint") {
            dexkit.findMethod {
                matcher {
                    returnType = "void"
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    paramTypes("int", null, "boolean")
                    opNames = listOf(
                        "iget",
                        "if-ne",
                        "iget-object",
                        "if-ne",
                        "iget-boolean",
                        "if-eq",
                        "goto",
                        "return-void",
                        "sget-object",
                        "const/4",
                        "if-ne",
                        "iput-boolean"
                    )
                }
            }.single()
        }.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }

    fun HideMusicVideoAds() {
        // ShowMusicVideoAdsParentFingerprint
        getDexMethod("ShowMusicVideoAdsMethod") {
            dexkit.findMethod {
                matcher {
                    usingStrings(
                        listOf("maybeRegenerateCpnAndStatsClient called unexpectedly, but no error."),
                        StringMatchType.Equals
                    )
                }
            }.single().also {
                Logger.printInfo { "ShowMusicVideoAdsParentFingerprint Matches: ${it.toDexMethod()}" }
            }.invokes.findMethod {
                matcher {
                    paramTypes("boolean")
                }
            }.single()
        }.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = false
            }
        })
    }
}
