package io.github.chsbuffer.revancedxposed.youtube.interaction

import android.view.View
import app.revanced.extension.shared.Logger
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook

lateinit var addBottomControl: MutableList<() -> View>

fun YoutubeHook.PlayerControls() {
    val id =
        app.resources.getIdentifier("youtube_controls_bottom_ui_container", "id", app.packageName)
    Logger.printDebug { "youtube_controls_bottom_ui_container: $id" }

}