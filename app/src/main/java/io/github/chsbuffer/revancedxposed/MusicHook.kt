package io.github.chsbuffer.revancedxposed

import android.app.Application
import android.view.View
import app.revanced.extension.shared.Logger
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.InvocationTargetError
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier

class MusicHook(app: Application, lpparam: LoadPackageParam) : Cache(app, lpparam) {
    override val hooks = arrayOf(
        ::HideMusicVideoAds,
        ::MinimizedPlayback,
        ::RemoveUpgradeButton,
        ::HideGetPremium,
        ::EnableExclusiveAudioPlayback,
    )

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
        }.let {
            XposedBridge.hookMethod(
                it.getMethodInstance(lpparam.classLoader), XC_MethodReplacement.returnConstant(true)
            )
        }
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
        }.let {
            XposedBridge.hookMethod(
                it.getMethodInstance(lpparam.classLoader),
                object : XC_MethodHook() {
                    lateinit var hook: XC_MethodHook.Unhook

                    override fun beforeHookedMethod(param: MethodHookParam) {
                        hook = XposedHelpers.findAndHookMethod("android.view.View",
                            lpparam.classLoader,
                            "setVisibility",
                            Int::class.java,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    param.args[0] = View.GONE
                                }
                            })
                    }

                    override fun afterHookedMethod(param: MethodHookParam?) {
                        hook.unhook()
                    }
                })
        }
    }

    fun RemoveUpgradeButton() {
        // PivotBarConstructorFingerprint
        getDexMethod("PivotBarConstructorFingerprint") {
            val result = dexkit.findMethod {
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
            }.single()
            getString("pivotBarElementField") {
                result.declaredClass!!.fields.single { f -> f.typeName == "java.util.List" }.fieldName
            }
            result
        }.let {
            val pivotBarElementField =
                getString("pivotBarElementField") { throw Exception("WTF, Shouldn't i already searched?") }
            XposedBridge.hookMethod(it.getConstructorInstance(lpparam.classLoader),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val list = XposedHelpers.getObjectField(
                            param.thisObject, pivotBarElementField
                        )
                        try {
                            XposedHelpers.callMethod(list, "remove", 4)
                        } catch (e: InvocationTargetError) {
                            if (e.cause !is IndexOutOfBoundsException)
                                throw e
                        }
                    }
                })
        }
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
        }.let {
            XposedBridge.hookMethod(
                it.getMethodInstance(lpparam.classLoader), XC_MethodReplacement.returnConstant(true)
            )
        }

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
        }.let {
            XposedBridge.hookMethod(
                it.getMethodInstance(lpparam.classLoader), XC_MethodReplacement.DO_NOTHING
            )
        }
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
        }.let {
            XposedBridge.hookMethod(it.getMethodInstance(lpparam.classLoader),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = false
                    }
                })
        }
    }
}
