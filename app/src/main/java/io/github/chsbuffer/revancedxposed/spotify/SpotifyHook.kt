package io.github.chsbuffer.revancedxposed.spotify

import android.app.Application
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.spotify.misc.UnlockPremiumPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.ScopedHookSafe
import io.github.chsbuffer.revancedxposed.callMethod
import io.github.chsbuffer.revancedxposed.findField
import io.github.chsbuffer.revancedxposed.findFirstFieldByExactType
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.strings
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field

@Suppress("UNCHECKED_CAST")
class SpotifyHook(app: Application, lpparam: LoadPackageParam) : BaseHook(app, lpparam) {
    override val hooks = arrayOf(
        ::Extension,
        ::SanitizeSharingLinks,
        ::UnlockPremium,
    )

    val IS_SPOTIFY_LEGACY_APP_TARGET =
        runCatching { classLoader.loadClass("com.spotify.music.MainActivity") }.isSuccess

    fun Extension() {
        // Logger
        Utils.setContext(app)

        UnlockPremiumPatch.IS_SPOTIFY_LEGACY_APP_TARGET = IS_SPOTIFY_LEGACY_APP_TARGET
        UnlockPremiumPatch.classLoader = classLoader
    }

    fun UnlockPremium() {
        // Override the attributes map in the getter method.
        getDexMethod("productStateProtoFingerprint") {
            findClass {
                matcher {
                    if (IS_SPOTIFY_LEGACY_APP_TARGET)
                        className("com.spotify.ucs.proto.v0.UcsResponseWrapper\$AccountAttributesResponse")
                    else
                        className("com.spotify.remoteconfig.internal.ProductStateProto")
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
            val field = getDexField("attributesMapField").toField()
            override fun beforeHookedMethod(param: MethodHookParam) {
                Logger.printDebug { field.get(param.thisObject)!!.toString() }
                UnlockPremiumPatch.overrideAttribute(field.get(param.thisObject) as Map<String, *>)
            }
        })

        // Add the query parameter trackRows to show popular tracks in the artist page.
        getDexMethod("buildQueryParametersFingerprint") {
            findMethod {
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

        // following patches may not support legacy Spotify version.
        if (IS_SPOTIFY_LEGACY_APP_TARGET) {
            Utils.showToastShort("Patching a legacy Spotify version. Patch functionality may be limited.")
            return
        }

        // Enable choosing a specific song/artist via Google Assistant.
        getDexMethod("contextFromJsonFingerprint") {
            findMethod {
                matcher {
                    declaredClass("com.spotify.voiceassistants.playermodels.ContextJsonAdapter")
                    name("fromJson")
                    opcodes(
                        Opcode.INVOKE_STATIC,
                        Opcode.MOVE_RESULT_OBJECT,
                        Opcode.INVOKE_VIRTUAL,
                        Opcode.MOVE_RESULT_OBJECT,
                        Opcode.INVOKE_STATIC
                    )
                }
            }.single()
        }.hookMethod(object : XC_MethodHook() {
            fun removeStationString(field: Field, obj: Any) {
                field.set(obj, UnlockPremiumPatch.removeStationString(field.get(obj) as String))
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val thiz = param.result
                val clazz = param.result.javaClass
                removeStationString(clazz.findField("uri"), thiz)
                removeStationString(clazz.findField("url"), thiz)
            }
        })

        // Disable forced shuffle when asking for an album/playlist via Google Assistant.
        XposedHelpers.findAndHookMethod(
            "com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides\$Builder",
            classLoader,
            "build",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.thisObject.callMethod("shufflingContext", false)
                }
            })

        // Disable the "Spotify Premium" upsell experiment in context menus.
        val contextMenuExperiments = getDexMethod("contextMenuExperimentsFingerprint") {
            findMethod {
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
        val checkExperimentsMethod = getDexMethod("checkExperiments").toMethod()
        contextMenuExperiments.hookMethod(ScopedHookSafe(checkExperimentsMethod) {
            returnConstant(false)
        })

        // Remove ads sections from home.
        getDexMethod("homeStructureFingerprint") {
            findMethod {
                matcher {
                    declaredClass("homeapi.proto.HomeStructure", StringMatchType.EndsWith)
                    opcodes(Opcode.IGET_OBJECT, Opcode.RETURN_OBJECT)
                }
            }.single()
        }.hookMethod(object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val sections = param.result
                // Set sections mutable
                sections.javaClass.findFirstFieldByExactType(Boolean::class.java)
                    .set(sections, true)
                UnlockPremiumPatch.removeHomeSections(param.result as MutableList<Any>)
            }
        })

        // Remove pendragon (pop up ads) requests and return the errors instead.
        val replaceFetchRequestSingleWithError = object : XC_MethodHook() {
            val justMethod =
                DexMethod("Lio/reactivex/rxjava3/core/Single;->just(Ljava/lang/Object;)Lio/reactivex/rxjava3/core/Single;").toMethod()

            val onErrorField =
                DexField("Lio/reactivex/rxjava3/internal/operators/single/SingleOnErrorReturn;->b:Lio/reactivex/rxjava3/functions/Function;").toField()

            override fun afterHookedMethod(param: MethodHookParam) {
                if (!param.result.javaClass.name.endsWith("SingleOnErrorReturn")) return
                val justError = justMethod.invoke(null, onErrorField.get(param.result))
                param.result = justError
            }
        }

        getDexMethod("pendragonJsonFetchMessageRequestFingerprint") {
            findMethod {
                matcher {
                    name("apply")
                    addInvoke {
                        name("<init>")
                        declaredClass("FetchMessageRequest", StringMatchType.EndsWith)
                    }
                }
            }.single()
        }.hookMethod(replaceFetchRequestSingleWithError)

        getDexMethod("pendragonJsonFetchMessageListRequestFingerprint") {
            findMethod {
                matcher {
                    name("apply")
                    addInvoke {
                        name("<init>")
                        declaredClass("FetchMessageListRequest", StringMatchType.EndsWith)
                    }
                }
            }.single()
        }.hookMethod(replaceFetchRequestSingleWithError)
    }
}