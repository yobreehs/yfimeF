package io.github.chsbuffer.revancedxposed.photomath

import android.app.Application
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.fingerprint
import org.luckypray.dexkit.query.enums.StringMatchType

class PhotomathHook(app: Application, lpparam: XC_LoadPackage.LoadPackageParam) : BaseHook(
    app, lpparam
) {
    override val hooks = arrayOf(::UnlockPlus)

    fun UnlockPlus() {
        dependsOn(::EnableBookpoint)
        getDexMethod("isPlusUnlockedFingerprint") {
            fingerprint {
                accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
                returns("Z")
                strings("genius")
                classMatcher { className(".User", StringMatchType.EndsWith) }
            }
        }.hookMethod(XC_MethodReplacement.returnConstant(true))
    }

    fun EnableBookpoint() {
        getDexMethod("isBookpointEnabledFingerprint") {
            fingerprint {
                accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
                returns("Z")
                parameters()
                strings(
                    "NoGeoData",
                    "NoCountryInGeo",
                    "RemoteConfig",
                    "GeoRCMismatch"
                )
            }
        }.hookMethod(XC_MethodReplacement.returnConstant(true))
    }
}