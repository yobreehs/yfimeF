package io.github.chsbuffer.revancedxposed.youtube.misc

import android.annotation.SuppressLint
import android.view.View
import app.revanced.extension.shared.Utils
import app.revanced.extension.youtube.shared.NavigationBar
import app.revanced.extension.youtube.shared.NavigationBar.NavigationButton
import de.robv.android.xposed.XC_MethodHook
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.accessFlags
import io.github.chsbuffer.revancedxposed.definingClass
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.literal
import io.github.chsbuffer.revancedxposed.parameters
import io.github.chsbuffer.revancedxposed.returns
import io.github.chsbuffer.revancedxposed.strings
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import java.util.EnumMap


@JvmField
val hookNavigationButtonCreated: MutableList<(NavigationButton, View) -> Unit> = mutableListOf()

@SuppressLint("NonUniqueDexKitData")
fun YoutubeHook.NavigationBarHook() {

    val initializeButtonsFingerprint = getDexMethod("initializeButtonsFingerprint") {
        val pivotBarConstructorFingerprint = fingerprint {
            accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
            strings("com.google.android.apps.youtube.app.endpoint.flags")
        }

        val imageOnlyTabResourceId = Utils.getResourceIdentifier("image_only_tab", "layout")
        pivotBarConstructorFingerprint.declaredClass!!.findMethod {
            matcher {
                accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
                returns("V")
                literal { imageOnlyTabResourceId }
            }
        }.single()
    }

    // Hook the current navigation bar enum value. Note, the 'You' tab does not have an enum value.
    val navigationEnumClass = getDexClass("navigationEnumClass") {
        fingerprint {
            accessFlags(AccessFlags.STATIC, AccessFlags.CONSTRUCTOR)
            strings(
                "PIVOT_HOME",
                "TAB_SHORTS",
                "CREATION_TAB_LARGE",
                "PIVOT_SUBSCRIPTIONS",
                "TAB_ACTIVITY",
                "VIDEO_LIBRARY_WHITE",
                "INCOGNITO_CIRCLE",
            )
        }.declaredClass!!
    }

    val getNavigationEnumMethod = getDexMethod("navigationEnumClass_INVOKE_STATIC") {
        this.getMethodData(initializeButtonsFingerprint.toString())!!.invokes.findMethod {
            matcher {
                declaredClass(navigationEnumClass.className)
                accessFlags(AccessFlags.STATIC)
            }
        }.single()
    }
    initializeButtonsFingerprint.hookMethod(ScopedHook(getNavigationEnumMethod.toMethod()) {
        after { NavigationBar.setLastAppNavigationEnum(param.result as Enum<*>) }
    })

    // Hook the creation of navigation tab views.
    val pivotBarButtonsCreateDrawableViewFingerprint =
        getDexMethod("pivotBarButtonsCreateDrawableViewFingerprint") {
            findMethod {
                matcher {
                    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
                    returns("Landroid/view/View;")
                    declaredClass {
                        descriptor =
                            "Lcom/google/android/libraries/youtube/rendering/ui/pivotbar/PivotBar;"
                    }
                }
            }.single {
                it.paramTypes.firstOrNull()?.descriptor == "Landroid/graphics/drawable/Drawable;"
            }
        }

    initializeButtonsFingerprint.hookMethod(ScopedHook(pivotBarButtonsCreateDrawableViewFingerprint.toMethod()) {
        after { NavigationBar.navigationTabLoaded(param.result as View) }
    })

    val pivotBarButtonsCreateResourceViewFingerprint =
        getDexMethod("pivotBarButtonsCreateResourceViewFingerprint") {
            fingerprint {
                accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
                returns("Landroid/view/View;")
                parameters("L", "Z", "I", "L")
                definingClass("Lcom/google/android/libraries/youtube/rendering/ui/pivotbar/PivotBar;")
            }
        }

    initializeButtonsFingerprint.hookMethod(ScopedHook(pivotBarButtonsCreateResourceViewFingerprint.toMethod()) {
        after { NavigationBar.navigationImageResourceTabLoaded(param.result as View) }
    })

    // Fix YT bug of notification tab missing the filled icon.
    val tabActivityCairo =
        navigationEnumClass.toClass().enumConstants?.firstOrNull { (it as? Enum<*>)?.name == "TAB_ACTIVITY_CAIRO" } as? Enum<*>
    if (tabActivityCairo != null) {
        getDexMethod("setEnumMapFingerprint") {
            fingerprint {
                accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
                literal {
                    Utils.getResourceIdentifier("yt_fill_bell_black_24", "drawable")
                }
            }
        }.hookMethod(object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val obj = param.thisObject
                param.thisObject.javaClass.declaredFields.forEach {
                    it.isAccessible = true
                    val enumMap = it.get(obj) as? EnumMap<*, *>
                    if (enumMap !is EnumMap<*, *>) return@forEach
                    // check is valueType int (resource id)
                    val valueType = enumMap.values.first()::class.java
                    if (valueType != Int::class.java) return@forEach
                    NavigationBar.setCairoNotificationFilledIcon(enumMap, tabActivityCairo)
                }
            }
        })
    }
}