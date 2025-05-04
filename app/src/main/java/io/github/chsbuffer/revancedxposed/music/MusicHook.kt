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
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.opcodes
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
            findMethod {
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
            findMethod {
                matcher {
                    modifiers = Modifier.FINAL or Modifier.PUBLIC
                    usingEqStrings("FEmusic_history", "FEmusic_offline")
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
            findMethod {
                matcher {
                    name = "<init>"
                    returnType = "void"
                    modifiers = Modifier.PUBLIC
                    paramTypes(null, "boolean")
                    opcodes(
                        Opcode.CHECK_CAST,
                        Opcode.INVOKE_INTERFACE,
                        Opcode.GOTO,
                        Opcode.IPUT_OBJECT,
                        Opcode.RETURN_VOID
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
            findMethod {
                matcher {
                    returnType = "boolean"
                    modifiers = Modifier.PUBLIC or Modifier.STATIC
                    paramCount = 1
                    opcodes(
                        Opcode.CONST_4,
                        Opcode.IF_EQZ,
                        Opcode.IGET,
                        Opcode.AND_INT_LIT16,
                        Opcode.IF_EQZ,
                        Opcode.IGET_OBJECT,
                        Opcode.IF_NEZ,
                        Opcode.SGET_OBJECT,
                        Opcode.IGET,
                    )
                }
            }.single()
        }.hookMethod(XC_MethodReplacement.returnConstant(true))

        // KidsMinimizedPlaybackPolicyControllerFingerprint
        getDexMethod("KidsMinimizedPlaybackPolicyControllerFingerprint") {
            findMethod {
                matcher {
                    returnType = "void"
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    paramTypes("int", null, "boolean")
                    opcodes(
                        Opcode.IGET,
                        Opcode.IF_NE,
                        Opcode.IGET_OBJECT,
                        Opcode.IF_NE,
                        Opcode.IGET_BOOLEAN,
                        Opcode.IF_EQ,
                        Opcode.GOTO,
                        Opcode.RETURN_VOID,
                        Opcode.SGET_OBJECT,
                        Opcode.CONST_4,
                        Opcode.IF_NE,
                        Opcode.IPUT_BOOLEAN,
                    )
                }
            }.single()
        }.hookMethod(XC_MethodReplacement.DO_NOTHING)
    }

    fun HideMusicVideoAds() {
        // ShowMusicVideoAdsParentFingerprint
        getDexMethod("ShowMusicVideoAdsMethod") {
            findMethod {
                matcher {
                    usingEqStrings(
                        "maybeRegenerateCpnAndStatsClient called unexpectedly, but no error."
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
