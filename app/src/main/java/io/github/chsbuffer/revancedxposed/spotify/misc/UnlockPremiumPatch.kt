package io.github.chsbuffer.revancedxposed.spotify.misc

import app.revanced.extension.shared.Logger
import app.revanced.extension.spotify.misc.UnlockPremiumPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.callMethod
import io.github.chsbuffer.revancedxposed.findFieldOrNull
import io.github.chsbuffer.revancedxposed.findFirstFieldByExactTypeOrNull
import io.github.chsbuffer.revancedxposed.settings.ModuleSettings
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
    val premiumUnlock = ModuleSettings.isEnabled(ModuleSettings.PREMIUM_UNLOCK)
    val blockAds = ModuleSettings.isEnabled(ModuleSettings.BLOCK_ADS)

    Logger.printInfo {
        "Femify active: premium unlock=$premiumUnlock, ad block=$blockAds"
    }

    // Override the attributes map entries in the getter method.
    guarded("productStateProtoFingerprint") {
        ::productStateProtoFingerprint.hookMethod {
            val field = ::attributesMapField.field
            before { param ->
                val attributes = field.get(param.thisObject) as Map<String, *>
                if (premiumUnlock) {
                    UnlockPremiumPatch.overrideAttributes(attributes)
                } else {
                    // Premium spoof is off: keep the server values, but still dump them once so bug
                    // reports show what the client actually believes about the account.
                    UnlockPremiumPatch.logProductState(attributes)
                }
            }
        }
    }

    // Add the query parameter trackRows to show popular tracks in the artist page.
    guarded("buildQueryParametersFingerprint") {
        if (!premiumUnlock) return@guarded
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
        if (!premiumUnlock) return@guarded
        ::contextFromJsonFingerprint.hookMethod {
            fun removeStationString(clazz: Class<*>, obj: Any, name: String) {
                val field = clazz.findFieldOrNull(name) ?: return
                val value = runCatching { field.get(obj) as? String }.getOrNull() ?: return
                runCatching { field.set(obj, UnlockPremiumPatch.removeStationString(value)) }
            }

            after { param ->
                val result = param.result ?: return@after
                val clazz = result.javaClass
                removeStationString(clazz, result, "uri")
                removeStationString(clazz, result, "url")
            }
        }
    }

    // Disable forced shuffle when asking for an album/playlist via Google Assistant.
    guarded("AutoValue_PlayerOptionOverrides.Builder.build") {
        if (!premiumUnlock) return@guarded
        XposedHelpers.findAndHookMethod(
            "com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides\$Builder",
            classLoader,
            "build",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Never let an exception escape into Spotify's player code.
                    try {
                        param.thisObject.callMethod("shufflingContext", false)
                    } catch (t: Throwable) {
                        Logger.printException({ "UnlockPremium: shufflingContext override failed" }, t)
                    }
                }
            })
    }

    // Hook the method which adds context menu items and return before adding
    // any Premium upsell item.
    guarded("contextMenuViewModel constructors") {
        if (!premiumUnlock) return@guarded
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
        if (!blockAds) return@guarded
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
        if (!blockAds) return@guarded
        ::browseStructureGetSectionsFingerprint.hookMethod {
            after { param ->
                val sections = param.result
                sections.makeSectionsMutable()
                UnlockPremiumPatch.removeBrowseSections(param.result as MutableList<*>)
            }
        }
    }

    // Remove the embedded brand-ad card from the Now Playing View scroll feed (the banner shown
    // below the lyrics card).
    guarded("npvScrollStructureGetSectionsFingerprint") {
        if (!blockAds) return@guarded
        ::npvScrollStructureGetSectionsFingerprint.hookMethod {
            after { param ->
                val sections = param.result
                sections.makeSectionsMutable()
                UnlockPremiumPatch.removeNpvBrandAdSections(param.result as MutableList<*>)
            }
        }
    }

    // Remove pendragon (pop up ads) requests and return the errors instead.
    guarded("pendragon replaceFetchRequestSingleWithError") {
        if (!blockAds) return@guarded
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

    // Playback diagnostics. Release builds of Spotify log nothing, so a track that stalls or is
    // skipped leaves no trace in logcat. Every betamax (playback engine) failure is logged here,
    // including its message, so the next recording explains why the player moved to the next track.
    guarded("playbackExceptionLogging") {
        PLAYBACK_EXCEPTION_CLASSES.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            runCatching {
                XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val details = param.args.joinToString(", ") { it?.toString() ?: "null" }
                        Logger.printInfo { "Playback exception ${clazz.simpleName}: $details" }
                    }
                })
            }.onSuccess {
                Logger.printDebug { "Playback diagnostics: watching ${clazz.simpleName}" }
            }.onFailure { err ->
                Logger.printDebug { "Playback diagnostics: cannot watch ${clazz.simpleName}: ${err.javaClass.simpleName}" }
            }
        }
    }
}

/**
 * Playback engine (betamax) failures worth logging. They cover the cases that make a track stop
 * after a few seconds: unplayable tracks, a killed player, a broken manifest, storage/drm issues.
 */
/**
 * Diagnostic-only hooks: Spotify's player is native and logs nothing in a release build, so the only
 * way to tell *why* a track stops is to watch the two models the Java layer builds from it —
 * `PlayerState` (position/duration/buffering of the current track) and `PlayerError` (the error type
 * plus the server's `reasons` string). No behaviour is changed here.
 */
fun SpotifyHook.PlaybackDiagnostics() {
    guarded("playerStateLogging") {
        val stateClass = classLoader.loadClass("com.spotify.player.model.AutoValue_PlayerState")
        XposedBridge.hookAllConstructors(stateClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                // Never let a diagnostic throw into Spotify's state constructor: that would break
                // the player and the UI instead of only losing a log line.
                runCatching { logPlayerState(param.thisObject) }
            }
        })
        Logger.printDebug { "Playback diagnostics: watching PlayerState" }
    }

    guarded("playerErrorLogging") {
        val errorClass = classLoader.loadClass("com.spotify.player.model.AutoValue_PlayerError")
        XposedBridge.hookAllConstructors(errorClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching { Logger.printInfo { "Playback error: ${param.thisObject}" } }
            }
        })
        Logger.printDebug { "Playback diagnostics: watching PlayerError" }
    }
}

private var lastStateKey: String? = null
private var lastStateAt: Long = 0L

@Suppress("UNCHECKED_CAST")
private fun logPlayerState(state: Any) {
    fun read(name: String): Any? = runCatching { XposedHelpers.callMethod(state, name) }.getOrNull()

    val track = read("track").toString()
    val index = read("index").toString()
    val playing = read("isPlaying")
    val buffering = read("isBuffering")
    val paused = read("isPaused")
    val position = read("positionAsOfTimestamp").toString()
    val duration = read("duration").toString()

    // State objects are rebuilt on every emission; log on change, plus a heartbeat every 3 s so a
    // frozen position (audio data stopped, UI still interpolating) is visible.
    val key = "$track|$index|$playing|$buffering|$paused"
    val now = System.currentTimeMillis()
    if (key == lastStateKey && now - lastStateAt < 3_000) return
    lastStateKey = key
    lastStateAt = now

    Logger.printInfo {
        "Playback state: track=$track index=$index pos=$position dur=$duration " +
                "playing=$playing buffering=$buffering paused=$paused"
    }
}

private val PLAYBACK_EXCEPTION_CLASSES = listOf(
    "com.spotify.betamax.player.exception.BetamaxException",
    "com.spotify.betamax.player.exception.BetamaxPlaybackException",
    "com.spotify.betamax.player.exception.UnplayablePlaybackException",
    "com.spotify.betamax.player.exception.PlaybackKilledException",
    "com.spotify.betamax.player.exception.ManifestLoadException",
    "com.spotify.betamax.playerimpl.tracking.PendingStorageException",
    "com.spotify.betamax.playerimpl.exo.AudioSinkProcessingException",
    "com.spotify.betamax.offline.LicensePreparationException",
    "com.spotify.betamax.common.drm.DrmException",
    "com.spotify.betamax.common.drm.DrmLicenseServerException",
    "com.spotify.betamax.common.SunsetUpgradeRequiredException",
)

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