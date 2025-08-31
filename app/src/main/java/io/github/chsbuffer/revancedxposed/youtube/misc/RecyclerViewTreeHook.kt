package io.github.chsbuffer.revancedxposed.youtube.misc

import android.support.v7.widget.RecyclerView
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.scopedHook
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.result.ClassData

private val ClassData.isObject
    get() = this.descriptor.startsWith("L")

val addRecyclerViewTreeHook = mutableListOf<(RecyclerView) -> Unit>()

fun YoutubeHook.recyclerViewTreeHook() {
    getDexMethod("recyclerViewTreeObserverFingerprint") {
        fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
            returns("V")
            opcodes(
                Opcode.CHECK_CAST,
                Opcode.NEW_INSTANCE,
                Opcode.INVOKE_DIRECT,
                Opcode.INVOKE_VIRTUAL,
                Opcode.NEW_INSTANCE,
            )
            strings("LithoRVSLCBinder")
        }.also { method ->
            val recyclerView = method.paramTypes[1]
            getDexMethod("RecyclerView_addOnScrollListener") {
                method.invokes.single {
                    it.declaredClass == recyclerView && it.paramTypes.singleOrNull { clz -> clz.isObject } != null
                }
            }
        }
    }.hookMethod(
        scopedHook(
            getDexMethod("RecyclerView_addOnScrollListener").toMember()
        ) {
            before {
                val recyclerView = it.thisObject as RecyclerView
                addRecyclerViewTreeHook.forEach { hook ->
                    hook(recyclerView)
                }
            }
        })
}
