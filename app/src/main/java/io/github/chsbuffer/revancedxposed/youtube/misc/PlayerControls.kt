package io.github.chsbuffer.revancedxposed.youtube.misc

import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageView
import app.revanced.extension.shared.Utils
import app.revanced.extension.youtube.patches.PlayerControlsPatch
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.scopedHook
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.wrap.DexMethod

class BottomControl(
    val id: Int,
    @JvmField val initializeButton: (controlsView: ViewGroup) -> Unit,
    @JvmField val setVisibility: (Boolean, Boolean) -> Unit,
    @JvmField val setVisibilityImmediate: (Boolean) -> Unit,
    // Patch works without this hook, but it is needed to use the correct fade out animation
    // duration when tapping the overlay to dismiss.
    @JvmField val setVisibilityNegatedImmediate: () -> Unit
)

@JvmField
var visibilityImmediateCallbacksExistModified = false

@JvmField
val bottomControls = mutableListOf<BottomControl>()

private val bottomControlLayouts = mutableListOf<Int>()
fun addBottomControl(layout: Int) {
    bottomControlLayouts.add(layout)
}

fun initializeBottomControl(bottomControl: BottomControl) {
    bottomControls.add(bottomControl)

    if (!visibilityImmediateCallbacksExistModified) {
        visibilityImmediateCallbacksExistModified = true
    }
}

fun YoutubeHook.PlayerControls() {
    DexMethod("Landroid/view/ViewStub;->inflate()Landroid/view/View;").hookMethod {
        after {
            val viewStub = it.thisObject as ViewStub
            val viewStubName = Utils.getContext().resources.getResourceName(viewStub.id)
//            Logger.printDebug { "ViewStub->inflate()" + viewStubName }

            if (viewStubName.endsWith("bottom_ui_container_stub")) {
//                Logger.printDebug { "inject into $viewStubName" }
                val viewGroup = it.result as ViewGroup

                bottomControlLayouts.forEach { layout ->
                    viewStub.layoutInflater.inflate(layout, viewGroup, true)
                }

                bottomControls.forEach { bottomControl -> bottomControl.initializeButton(viewGroup) }
            }
        }

        initInjectVisibilityCheckCall()

        val youtube_controls_bottom_ui_container =
            Utils.getResourceIdentifier("youtube_controls_bottom_ui_container", "id")

        DexMethod("Landroid/support/constraint/ConstraintLayout;->onLayout(ZIIII)V").hookMethod {
            after {
                val controlsView = it.thisObject as ViewGroup
                if (controlsView.id != youtube_controls_bottom_ui_container) return@after

                var rightButton =
                    Utils.getChildViewByResourceName<View>(controlsView, "fullscreen_button")

                for (bottomControl in bottomControls) {
                    val leftButton = controlsView.findViewById<View>(bottomControl.id)
                    // put this button to the left
                    leftButton.x = rightButton.x - leftButton.width
                    leftButton.y = rightButton.y
                    rightButton = leftButton
                }
            }
        }
    }
}

private fun YoutubeHook.initInjectVisibilityCheckCall() {
    getDexMethod("controlsOverlayVisibilityFingerprint") {
        val controls_layout_stub_id = Utils.getResourceIdentifier("controls_layout_stub", "id")
        val playerTopControlsInflateFingerprint = fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            returns("V")
            parameters()
            literal { controls_layout_stub_id }
        }

        fingerprint {
            accessFlags(AccessFlags.PRIVATE, AccessFlags.FINAL)
            returns("V")
            parameters("Z", "Z")
            classMatcher {
                descriptor = playerTopControlsInflateFingerprint.declaredClass!!.descriptor
            }
        }
    }.hookMethod {
        before { param ->
            bottomControls.forEach {
                it.setVisibility(param.args[0] as Boolean, param.args[1] as Boolean)
            }
//            Logger.printDebug { "setVisibility(visible: ${param.args[0]}, animated: ${param.args[1]})" }
        }
    }

    // Hook the fullscreen close button.  Used to fix visibility
    // when seeking and other situations.
    val fullscreen_button_id = Utils.getResourceIdentifier("fullscreen_button", "id")
    getDexMethod("overlayViewInflateFingerprint") {
        val heatseeker_viewstub_id = Utils.getResourceIdentifier("heatseeker_viewstub", "id")
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
            returns("V")
            parameters("Landroid/view/View;")
            methodMatcher {
                addUsingNumber(fullscreen_button_id)
                addUsingNumber(heatseeker_viewstub_id)
            }
        }
    }.hookMethod(scopedHook(DexMethod("Landroid/view/View;->findViewById(I)Landroid/view/View;").toMember()) {
        after {
            if (it.args[0] == fullscreen_button_id) {
                PlayerControlsPatch.setFullscreenCloseButton(it.result as ImageView)
            }
        }
    })

    //
    getDexMethod("motionEventFingerprint") {
        val youtubeControlsOverlayFingerprint = fingerprint {
            accessFlags(AccessFlags.PRIVATE, AccessFlags.FINAL)
            returns("V")
            parameters()
            methodMatcher {
                addInvoke { name = "setFocusableInTouchMode" }
                addUsingNumber(Utils.getResourceIdentifier("inset_overlay_view_layout", "id"))
                addUsingNumber(Utils.getResourceIdentifier("scrim_overlay", "id"))
            }
        }
        fingerprint {
            returns("V")
            parameters("Landroid/view/MotionEvent;")
            methodMatcher {
                addInvoke { name = "setTranslationY" }
            }
            classMatcher {
                descriptor = youtubeControlsOverlayFingerprint.declaredClass!!.descriptor
            }
        }
    }.hookMethod(scopedHook(DexMethod("Landroid/view/View;->setTranslationY(F)V").toMethod()) {
        after {
            // FIXME Animation lags behind
            bottomControls.forEach { it.setVisibilityNegatedImmediate() }
//            Logger.printDebug { "setVisibilityNegatedImmediate()" }
        }
    })
}
