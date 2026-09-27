package io.github.chsbuffer.revancedxposed.spotify.misc

import app.revanced.extension.shared.Logger
import app.revanced.extension.spotify.misc.UnlockPremiumPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.callMethod
import io.github.chsbuffer.revancedxposed.findField
import io.github.chsbuffer.revancedxposed.findFirstFieldByExactTypeOrNull
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Field

/**
 * Unlocks Spotify Premium and removes ad surfaces.
 *
 * Every block below is isolated with [guarded]: if the app changes a hooked class/method on a new
 * version, only that block is lost and is logged instead of silently disabling premium, ad removal
 * and the pendragon block all at once (which previously happened because one exception killed the
 * whole hook function).
 */
@Suppress("UNCHECKED_CAST")
fun SpotifyHook.UnlockPremium() {
    // Override the attributes map entries in the getter method.
    guarded("productStateProtoFingerprint") {
        ::productStateProtoFingerprint.hookMethod {
            val field = ::attributesMapField.field
            before { param ->
                Logger.printDebug { field.get(param.thisObject)!!.toString() }
                UnlockPremiumPatch.overrideAttributes(field.get(param.thisObject) as Map<String, *>)
            }
        }
    }

    // Add the query parameter trackRows to show popular tracks in the artist page.
    guarded("buildQueryParametersFingerprint") {
        ::buildQueryParametersFingerprint.hookMethod {
            after { param ->
                val result = param.result
                val FIELD = "checkDeviceCapability"
                if (result.toString().contains("${FIELD}=")) {
                    param.result = XposedBridge.invokeOriginalMethod(
                        param.method, param.thisObject, arrayOf(param.args[0], true)
                    )
                }
            }
        }
    }

    // Enable choosing a specific song/artist via Google Assistant.
    guarded("contextFromJsonFingerprint") {
        ::contextFromJsonFingerprint.hookMethod {
            fun removeStationString(field: Field, obj: Any) {
                field.set(obj, UnlockPremiumPatch.removeStationString(field.get(obj) as String))
            }

            after { param ->
                val thiz = param.result
                val clazz = param.result.javaClass
                removeStationString(clazz.findField("uri"), thiz)
                removeStationString(clazz.findField("url"), thiz)
            }
        }
    }

    // Disable forced shuffle when asking for an album/playlist via Google Assistant.
    guarded("AutoValue_PlayerOptionOverrides.Builder.build") {
        XposedHelpers.findAndHookMethod(
            "com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides\$Builder",
            classLoader,
            "build",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.thisObject.callMethod("shufflingContext", false)
                }
            })
    }

    // Hook the method which adds context menu items and return before adding
    // any Premium upsell item.
    guarded("contextMenuViewModel constructors") {
        val contextMenuViewModelClazz = ::contextMenuViewModelClass.clazz
        val contextMenuItemInterfaceClazz = ::contextMenuItemInterface.clazz
        val premiumItemResIdField = ::contextMenuPremiumItemResIdField.field

        XposedBridge.hookAllConstructors(
            contextMenuViewModelClazz, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val parameterTypes = (param.method as Constructor<*>).parameterTypes
                    Logger.printDebug {
                        "ContextMenuViewModel(${parameterTypes.joinToString(",") { it.name }})"
                    }
                    for (i in 0 until param.args.size) {
                        if (parameterTypes[i].name != "java.util.List") continue
                        val original = param.args[i] as? List<*> ?: continue
                        Logger.printDebug { "List value type: ${original.firstOrNull()?.javaClass}" }
                        val filtered = original.filter { item ->
                            item == null || !isPremiumUpsellItem(
                                item, contextMenuItemInterfaceClazz, premiumItemResIdField
                            )
                        }
                        param.args[i] = filtered
                        Logger.printDebug { "Filtered ${original.size - filtered.size} context menu items." }
                    }
                }
            })
    }

    // Remove ads sections from home.
    guarded("homeStructureGetSectionsFingerprint") {
        ::homeStructureGetSectionsFingerprint.hookMethod {
            after { param ->
                val sections = param.result
                sections.makeSectionsMutable()
                UnlockPremiumPatch.removeHomeSections(param.result as MutableList<*>)
            }
        }
    }
    // Remove ads sections from browser.
    guarded("browseStructureGetSectionsFingerprint") {
        ::browseStructureGetSectionsFingerprint.hookMethod {
            after { param ->
                val sections = param.result
                sections.makeSectionsMutable()
                UnlockPremiumPatch.removeBrowseSections(param.result as MutableList<*>)
            }
        }
    }

    // Remove pendragon (pop up ads) requests and return the errors instead.
    guarded("pendragon replaceFetchRequestSingleWithError") {
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

        ::pendragonJsonFetchMessageRequestFingerprint.hookMethod(replaceFetchRequestSingleWithError)
        ::pendragonJsonFetchMessageListRequestFingerprint.hookMethod(replaceFetchRequestSingleWithError)
    }
}

/**
 * Run [block], logging any exception. Used so that a single broken hook on a new app version
 * cannot disable all other premium hooks.
 */
private inline fun guarded(name: String, block: () -> Unit) {
    try {
        block()
    } catch (err: Throwable) {
        Logger.printException({ "UnlockPremium: hook '$name' failed with ${err.javaClass.simpleName}" }, err)
    }
}

/**
 * Protobuf repeated-field lists guard their mutability with a boolean flag, either as a boxed
 * [Boolean] or as a primitive [Boolean.TYPE] field, depending on the app build.
 */
private fun Any.makeSectionsMutable() {
    val field = javaClass.findFirstFieldByExactTypeOrNull(Boolean::class.java)
        ?: javaClass.declaredFields.firstOrNull { it.type == Boolean::class.javaPrimitiveType }
        ?: return
    field.isAccessible = true
    field.set(this, true)
}

private fun isPremiumUpsellItem(item: Any, itemInterface: Class<*>, resIdField: Field): Boolean {
    if (!itemInterface.isInstance(item)) return false
    val resId = runCatching {
        XposedHelpers.getObjectField(item, resIdField.name) as String
    }.getOrNull() ?: return false
    return resId.startsWith("premium_destination_")
}