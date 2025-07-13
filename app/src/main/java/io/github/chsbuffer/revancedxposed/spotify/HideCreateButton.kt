package io.github.chsbuffer.revancedxposed.spotify

import app.revanced.extension.spotify.layout.hide.createbutton.HideCreateButtonPatch
import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.fingerprint

fun SpotifyHook.HideCreateButton() {
    val oldNavigationBarAddItemMethod = runCatching {
        getDexMethod("oldNavigationBarAddItemFingerprint") {
            fingerprint {
                strings("Bottom navigation tabs exceeds maximum of 5 tabs")
            }
        }
    }.getOrNull()

    val navigationBarItemSetClassDef = runCatching {
        getDexClass("navigationBarItemSetClassFingerprint") {
            fingerprint {
                strings("NavigationBarItemSet(")
            }.declaredClass!!
        }
    }.getOrNull()

    if (navigationBarItemSetClassDef != null) {
        // Main patch for newest and most versions.
        // The NavigationBarItemSet constructor accepts multiple parameters which represent each navigation bar item.
        // Each item is manually checked whether it is not null and then added to a LinkedHashSet.
        // Since the order of the items can differ, we are required to check every parameter to see whether it is the
        // Create button. So, for every parameter passed to the method, invoke our extension method and overwrite it
        // to null in case it is the Create button.
        getDexMethod("navigationBarItemSetConstructorFingerprint") {
            fingerprint {
                accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
                // Make sure the method checks whether navigation bar items are null before adding them.
                // If this is not true, then we cannot patch the method and potentially transform the parameters into null.
                opcodes(Opcode.IF_EQZ, Opcode.INVOKE_VIRTUAL)
                classMatcher {
                    className = navigationBarItemSetClassDef.className
                }
                methodMatcher {
                    addInvoke { name = "add" }
                }
            }
        }.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                for ((i, arg) in param.args.withIndex()) {
                    param.args[i] = HideCreateButtonPatch.returnNullIfIsCreateButton(arg)
                }
            }
        })
    }

    @Suppress("IfThenToSafeAccess")
    if (oldNavigationBarAddItemMethod != null) {
        // In case an older version of the app is being patched, hook the old method which adds navigation bar items.
        // Return early if the navigation bar item title resource id is the old Create button title resource id.
        oldNavigationBarAddItemMethod.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                for (arg in param.args) {
                    if (arg !is Int) continue
                    if (HideCreateButtonPatch.isOldCreateButton(arg)) {
                        param.result = null
                        return
                    }
                }
            }
        })
    }
}