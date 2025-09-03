package io.github.chsbuffer.revancedxposed.youtube.misc.navigation

import android.annotation.SuppressLint
import android.view.View
import app.revanced.extension.youtube.shared.NavigationBar
import io.github.chsbuffer.revancedxposed.isStatic
import io.github.chsbuffer.revancedxposed.scopedHook
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import java.util.EnumMap

@JvmField
val hookNavigationButtonCreated: MutableList<(NavigationBar.NavigationButton, View) -> Unit> =
    mutableListOf()

@SuppressLint("NonUniqueDexKitData")
fun YoutubeHook.NavigationBarHook() {

    // Hook the current navigation bar enum value. Note, the 'You' tab does not have an enum value.
    ::initializeButtonsFingerprint.hookMethod(scopedHook(::getNavigationEnumMethod.member) {
        after { param -> NavigationBar.setLastAppNavigationEnum(param.result as Enum<*>) }
    })

    // Hook the creation of navigation tab views.
    ::initializeButtonsFingerprint.hookMethod(scopedHook(::pivotBarButtonsCreateDrawableViewFingerprint.member) {
        after { param -> NavigationBar.navigationTabLoaded(param.result as View) }
    })

    ::initializeButtonsFingerprint.hookMethod(scopedHook(::pivotBarButtonsCreateResourceViewFingerprint.member) {
        after { param -> NavigationBar.navigationImageResourceTabLoaded(param.result as View) }
    })

    // Fix YT bug of notification tab missing the filled icon.
    val tabActivityCairo =
        ::navigationEnumClass.clazz.enumConstants?.firstOrNull { (it as? Enum<*>)?.name == "TAB_ACTIVITY_CAIRO" } as? Enum<*>
    if (tabActivityCairo != null) {
        ::setEnumMapFingerprint.dexMethodList.forEach { setEnumMapFingerprint ->
            fun processFields(obj: Any?, clazz: Class<*>) {
                clazz.declaredFields.forEach { field ->
                    field.isAccessible = true
                    if (obj == null && !field.isStatic) return@forEach
                    val enumMap = field.get(obj) as? EnumMap<*, *> ?: return@forEach
                    // check is valueType int (resource id)
                    val valueType = enumMap.values.firstOrNull()?.javaClass ?: return@forEach
                    if (valueType != Int::class.javaObjectType) return@forEach
                    if (!enumMap.containsKey(tabActivityCairo))
                        NavigationBar.setCairoNotificationFilledIcon(enumMap, tabActivityCairo)
                }
            }

            if (setEnumMapFingerprint.isStaticInitializer) {
                processFields(null, classLoader.loadClass(setEnumMapFingerprint.declaredClassName))
            } else {
                setEnumMapFingerprint.hookMethod {
                    after { param ->
                        processFields(param.thisObject, param.thisObject.javaClass)
                    }
                }
            }
        }
    }
}