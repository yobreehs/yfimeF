package io.github.chsbuffer.revancedxposed.shared.misc

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import app.revanced.extension.shared.Utils
import io.github.chsbuffer.revancedxposed.BaseHook
import org.luckypray.dexkit.wrap.DexMethod

fun BaseHook.CheckRecycleBitmapMediaSession() {
    // Warning about YouTube and YouTube Music may crash on certain ROMs.

//    Emulate the issue
//    DexMethod("Landroid/media/MediaMetadata\$Builder;->build()Landroid/media/MediaMetadata;").hookMethod {
//        after {
//            val metadata = it.result as MediaMetadata
//            metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)?.recycle()
//            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.recycle()
//            metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.recycle()
//        }
//    }

    if (runCatching { DexMethod("Landroid/media/MediaMetadata\$Builder;->calculateSampleSize(IIII)I").toMember() }.isSuccess) {
//    if (true) {
        val context = Utils.getContext()
        val i = Intent(Intent.ACTION_VIEW)
            .addFlags(FLAG_ACTIVITY_NEW_TASK)
        i.data =
            Uri.parse("https://github.com/chsbuffer/ReVancedXposed/issues/29#issuecomment-3084170279")
        Utils.showToastLong("App Stability Issues Detected!")
        context.startActivity(i)
        Thread.sleep(30 * 1000)
    }
}