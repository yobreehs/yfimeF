package io.github.chsbuffer.revancedxposed.reddit

import android.app.Application
import android.view.View
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.chsbuffer.revancedxposed.BaseHook
import io.github.chsbuffer.revancedxposed.findFieldByExactType
import io.github.chsbuffer.revancedxposed.getObjectField
import io.github.chsbuffer.revancedxposed.setObjectField
import io.github.chsbuffer.revancedxposed.strings
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.wrap.DexMethod

class RedditHook(app: Application, lpparam: XC_LoadPackage.LoadPackageParam) :
    BaseHook(app, lpparam) {
    override val hooks = arrayOf(
        ::ExtensionHook, ::HideAds, ::SanitizeUrlQuery
    )

    fun ExtensionHook() {
        Utils.setContext(app)
    }

    fun HideAds() {
        dependsOn(
            ::HideBanner, ::HideComment
        )
        // region Filter promoted ads (does not work in popular or latest feed)
        getDexMethod("adPostFingerprint") {
            findMethod {
                matcher {
                    returnType("void")
                    // "children" are present throughout multiple versions
                    strings("children")
                    declaredClass(".Listing", StringMatchType.EndsWith)
                }
            }.single()
        }.hookMethod(object : XC_MethodHook() {
            val ilink = classLoader.loadClass("com.reddit.domain.model.ILink")
            val getPromoted = ilink.methods.single { it.name == "getPromoted" }
            override fun afterHookedMethod(param: MethodHookParam) {
                val arrayList = param.thisObject.getObjectField("children") as Iterable<Any?>
                val result = mutableListOf<Any?>()
                var filtered = 0
                for (item in arrayList) {
                    try {
                        if (item != null && ilink.isAssignableFrom(item.javaClass) == true && getPromoted.invoke(
                                item
                            ) == true
                        ) {
                            filtered++
                            continue
                        }
                    } catch (_: Throwable) {
                        Logger.printDebug { "not iLink, keep it" }
                        // not iLink, keep it
                    }
                    result.add(item)
                }
                Logger.printDebug { "Filtered $filtered ads in ${arrayList.count()} posts" }
                param.thisObject.setObjectField("children", result)
            }
        })

        // endregion

        // region Remove ads from popular and latest feed

        // The new feeds work by inserting posts into lists.
        // AdElementConverter is conveniently responsible for inserting all feed ads.
        // By removing the appending instruction no ad posts gets appended to the feed.
        getDexMethod("AdPostSection") {
            findClass {
                matcher {
                    usingStrings("AdPostSection(linkId=")
                }
            }.single().methods.single { it.isConstructor }
        }.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val sections = param.args[3] as MutableList<*>
                sections.javaClass.findFieldByExactType(Array<Any>::class.java)!!
                    .set(sections, emptyArray<Any>())
                Logger.printDebug { "Removed ads from popular and latest feed" }
            }
        })
    }

    fun HideBanner() {
        val merge_listheader_link_detail =
            Utils.getResourceIdentifier("merge_listheader_link_detail", "layout")
        val ad_view_stub = Utils.getResourceIdentifier("ad_view_stub", "id")
        DexMethod("Landroid/view/View;->inflate(Landroid/content/Context;ILandroid/view/ViewGroup;)Landroid/view/View;").hookMethod(
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val id = param.args[1] as Int
                        if (id == merge_listheader_link_detail) {
                            val view = param.result as View
                            val stub = view.findViewById<View>(ad_view_stub)
                            stub.layoutParams.apply {
                                width = 0
                                height = 0
                            }

                            Logger.printDebug { "Hide banner" }
                        }
                    }
                })
    }

    fun HideComment() {
        getDexMethod("hideCommentAdsFingerprint") {
            findMethod {
                matcher { usingStrings("link", "is not returning a link object") }
            }.single()
        }.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                Logger.printDebug { "Hide Comment" }
                param.result = Object()
            }
        })
    }

    fun SanitizeUrlQuery() {
        getDexMethod("shareLinkFormatterFingerprint") {
            findMethod {
                matcher {
                    returnType = "java.lang.String"
                    paramTypes("java.lang.String", null)
                    usingEqStrings(
                        "url",
                        "getQueryParameterNames(...)",
                        "getQueryParameters(...)",
                        "toString(...)"
                    )
                }
            }.single()
        }.hookMethod(object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = param.args[0]
            }
        })
    }
}