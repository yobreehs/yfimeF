package io.github.chsbuffer.revancedxposed.youtube.layout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.FrameLayout
import app.revanced.extension.shared.Utils
import app.revanced.extension.youtube.sponsorblock.SegmentPlaybackController
import app.revanced.extension.youtube.sponsorblock.ui.SponsorBlockAboutPreference
import app.revanced.extension.youtube.sponsorblock.ui.SponsorBlockPreferenceGroup
import app.revanced.extension.youtube.sponsorblock.ui.SponsorBlockStatsPreferenceCategory
import app.revanced.extension.youtube.sponsorblock.ui.SponsorBlockViewController
import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.addModuleAssets
import io.github.chsbuffer.revancedxposed.setObjectField
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.NonInteractivePreference
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceCategory
import io.github.chsbuffer.revancedxposed.shared.misc.settings.preference.PreferenceScreenPreference
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PlayerTypeHook
import io.github.chsbuffer.revancedxposed.youtube.misc.PreferenceScreen
import io.github.chsbuffer.revancedxposed.youtube.video.VideoIdPatch
import io.github.chsbuffer.revancedxposed.youtube.video.VideoInformationHook
import io.github.chsbuffer.revancedxposed.youtube.video.playerInitHooks
import io.github.chsbuffer.revancedxposed.youtube.video.videoIdHooks
import io.github.chsbuffer.revancedxposed.youtube.video.videoTimeHooks
import org.luckypray.dexkit.wrap.DexMethod
import kotlin.jvm.java

fun YoutubeHook.SponsorBlock() {
    dependsOn(
        ::VideoInformationHook,
        ::VideoIdPatch,
        ::PlayerTypeHook
    )

    PreferenceScreen.SPONSORBLOCK.addPreferences(
        // SB setting is old code with lots of custom preferences and updating behavior.
        // Added as a preference group and not a fragment so the preferences are searchable.
        PreferenceCategory(
            key = "revanced_settings_screen_10_sponsorblock",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = emptySet(), // Preferences are added by custom class at runtime.
            tag = SponsorBlockPreferenceGroup::class.java
        ),
        PreferenceCategory(
            key = "revanced_sb_stats",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = emptySet(), // Preferences are added by custom class at runtime.
            tag = SponsorBlockStatsPreferenceCategory::class.java
        ),
        PreferenceCategory(
            key = "revanced_sb_about",
            sorting = PreferenceScreenPreference.Sorting.UNSORTED,
            preferences = setOf(
                NonInteractivePreference(
                    key = "revanced_sb_about_api",
                    tag = SponsorBlockAboutPreference::class.java,
                    selectable = true,
                )
            )
        )
    )

    // Hook the video time methods.
    videoTimeHooks.add { SegmentPlaybackController.setVideoTime(it) }
    videoIdHooks.add { SegmentPlaybackController.setCurrentVideoId(it) }

    // Initialize the player controller.
    playerInitHooks.add { SegmentPlaybackController.initialize(it) }

    getDexClass("SeekbarClass") {
        findMethod {
            matcher {
                addEqString("timed_markers_width")
                returnType = "void"
            }
        }.single().declaredClass!!.also { clazz ->
            getDexField("SponsorBarRect") {
                clazz.findMethod {
                    matcher {
                        addInvoke {
                            name = "invalidate"
                            paramTypes("android.graphics.Rect")
                        }
                    }
                }.single().usingFields.last { it.field.typeName == "android.graphics.Rect" }.field
            }
            getDexMethod("seekbarOnDrawFingerprint") {
                clazz.findMethod {
                    matcher {
                        name = "onDraw"
                    }
                }.single()
            }
        }
    }

    // Seekbar drawing
    val seekbarOnDrawMethod = getDexMethod("seekbarOnDrawFingerprint")
    val seekbarDrawLock = ThreadLocal<Boolean?>()

    seekbarOnDrawMethod.hookMethod(object : XC_MethodHook() {
        val SponsorBarRectField = getDexField("SponsorBarRect").toField()
        override fun beforeHookedMethod(param: MethodHookParam) {
            // Get left and right of seekbar rectangle.
            SegmentPlaybackController.setSponsorBarRect(SponsorBarRectField.get(param.thisObject) as Rect)
            seekbarDrawLock.set(true)
        }

        override fun afterHookedMethod(param: MethodHookParam?) {
            seekbarDrawLock.set(false)
        }
    })

    // Find the drawCircle call and draw the segment before it.
    DexMethod("Landroid/graphics/RecordingCanvas;->drawCircle(FFFLandroid/graphics/Paint;)V").hookMethod(
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (seekbarDrawLock.get() != true) return
                val radius = (param.args[2] as Float).toInt()
                // Set the thickness of the segment.
                SegmentPlaybackController.setSponsorBarThickness(radius)

                SegmentPlaybackController.drawSponsorTimeBars(
                    param.thisObject as Canvas, param.args[1] as Float
                )
            }
        })

    // Initialize the SponsorBlock view.
    val inset_overlay_view_layout = Utils.getResourceIdentifier("inset_overlay_view_layout", "id")
    getDexMethod("controlsOverlayFingerprint") {
        findMethod {
            matcher {
                addUsingNumber(inset_overlay_view_layout)
                paramCount = 0
                returnType = "void"
            }
        }.single().also {
            getDexField("controlsOverlayParentLayout") { it.usingFields.first().field }
        }
    }.hookMethod(object : XC_MethodHook() {
        val field = getDexField("controlsOverlayParentLayout").toField()
        val id = inset_overlay_view_layout
        override fun afterHookedMethod(param: MethodHookParam) {
            val layout = field.get(param.thisObject) as FrameLayout
            layout.context.addModuleAssets()
            Utils.getContext().addModuleAssets()
            val overlay_view = layout.findViewById<ViewGroup>(id)
            SponsorBlockViewController.initialize(overlay_view)
        }
    })

    fun injectClassLoader(self: ClassLoader, host: ClassLoader) {
        val bootClassLoader = Context::class.java.classLoader!!
        host.setObjectField("parent", object : ClassLoader(bootClassLoader) {
            override fun findClass(name: String): Class<*> {
                try {
                    return bootClassLoader.loadClass(name)
                } catch (ignored: ClassNotFoundException) {
                }

                try {
                    if (name.startsWith("app.revanced")) return self.loadClass(name)
                } catch (ignored: ClassNotFoundException) {
                }

                throw ClassNotFoundException(name)
            }
        })
    }

    injectClassLoader(this::class.java.classLoader!!, classLoader)
}
