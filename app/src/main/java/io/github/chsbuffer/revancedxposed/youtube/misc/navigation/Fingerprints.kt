package io.github.chsbuffer.revancedxposed.youtube.misc.navigation

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.accessFlags
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.findMethodListDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.literal
import io.github.chsbuffer.revancedxposed.resourceMappings
import io.github.chsbuffer.revancedxposed.returns

// val actionBarSearchResultsFingerprint = fingerprint {
//    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
//    returns("Landroid/view/View;")
//    literal { actionBarSearchResultsViewMicId }
//}

val toolbarContainerId = resourceMappings["id", "toolbar_container"]
val toolbarLayoutFingerprint = fingerprint {
    accessFlags(AccessFlags.PROTECTED, AccessFlags.CONSTRUCTOR)
    literal { toolbarContainerId }
}

/**
 * Matches to https://android.googlesource.com/platform/frameworks/support/+/9eee6ba/v7/appcompat/src/android/support/v7/widget/Toolbar.java#963
 */
val appCompatToolbarBackButtonFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Landroid/graphics/drawable/Drawable;")
    parameters()
    classMatcher { descriptor = "Landroid/support/v7/widget/Toolbar;" }
}

val imageOnlyTabResourceId = resourceMappings["layout", "image_only_tab"]

/**
 * Matches to the class found in [pivotBarConstructorFingerprint].
 */
val initializeButtonsFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    literal { imageOnlyTabResourceId }
}

val getNavigationEnumMethod = findMethodDirect {
    initializeButtonsFingerprint().invokes.findMethod {
        matcher {
            declaredClass(navigationEnumClass(this@findMethodDirect).name)
            accessFlags(AccessFlags.STATIC)
        }
    }.single()
}

/**
 * Matches to the Enum class that looks up ordinal -> instance.
 */
val navigationEnumFingerprint = fingerprint {
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
}

val navigationEnumClass = findClassDirect { navigationEnumFingerprint().declaredClass!! }

val pivotBarButtonsCreateDrawableViewFingerprint = findMethodDirect {
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

val pivotBarButtonsCreateResourceViewFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Landroid/view/View;")
    parameters("L", "Z", "I", "L")
    classMatcher {
        descriptor = "Lcom/google/android/libraries/youtube/rendering/ui/pivotbar/PivotBar;"
    }
}

// fun indexOfSetViewSelectedInstruction(method: Method) = method.indexOfFirstInstruction {
//    opcode == Opcode.INVOKE_VIRTUAL && getReference<MethodReference>()?.name == "setSelected"
//}

val pivotBarButtonsViewSetSelectedFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    parameters("I", "Z")
    classMatcher {
        descriptor = "Lcom/google/android/libraries/youtube/rendering/ui/pivotbar/PivotBar;"
    }
    methodMatcher { addInvoke { name = "setSelected" } }
}

val pivotBarButtonsViewSetSelectedSubFingerprint = findMethodDirect {
    pivotBarButtonsViewSetSelectedFingerprint().invokes.single { it.name == "setSelected" }
}

val pivotBarConstructorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    strings("com.google.android.apps.youtube.app.endpoint.flags")
}

//val imageEnumConstructorFingerprint = fingerprint {
//    accessFlags(AccessFlags.STATIC, AccessFlags.CONSTRUCTOR)
//    strings("TAB_ACTIVITY_CAIRO")
//}

val setEnumMapFingerprint = findMethodListDirect {
    findMethod {
        matcher {
//            accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
            returns("V")
            literal {
                ytFillBellId
            }
        }
    }.filter { it.isConstructor || it.isStaticInitializer }
}

val ytFillBellId = resourceMappings["drawable", "yt_fill_bell_black_24"]
