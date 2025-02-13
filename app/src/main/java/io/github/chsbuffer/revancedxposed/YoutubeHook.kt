package io.github.chsbuffer.revancedxposed

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.view.View
import app.revanced.extension.shared.Logger
import app.revanced.extension.youtube.patches.components.AdsFilter
import app.revanced.extension.youtube.patches.components.LithoFilterPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Modifier
import java.nio.ByteBuffer

class YoutubeHook(app: Application, lpparam: LoadPackageParam) : Cache(app, lpparam) {
    override val hooks = arrayOf(
        ::VideoAds,
        ::BackgroundPlayback,
        ::RemoveTrackingQueryParameter,
        ::HideAds,
        ::LithoFilter,
    )

    val classLoader: ClassLoader = lpparam.classLoader

    fun VideoAds() {

        val LoadVideoAds = getDexMethod("LoadVideoAds") {
            dexkit.findMethod {
                matcher {
                    usingEqStrings(
                        listOf(
                            "TriggerBundle doesn't have the required metadata specified by the trigger ",
                            "Tried to enter slot with no assigned slotAdapter",
                            "Trying to enter a slot when a slot of same type and physical position is already active. Its status: ",
                        )
                    )
                }
            }.single()
        }

        XposedBridge.hookMethod(
            LoadVideoAds.getMethodInstance(classLoader), XC_MethodReplacement.DO_NOTHING
        )
    }

    fun BackgroundPlayback() {
        val prefBackgroundAndOfflineCategoryId = getNumber("prefBackgroundAndOfflineCategoryId") {
            app.resources.getIdentifier(
                "pref_background_and_offline_category", "string", app.packageName
            )
        }

        val BackgroundPlaybackManagerFingerprint =
            getDexMethod("BackgroundPlaybackManagerFingerprint") {
                dexkit.findMethod {
                    matcher {
                        returnType = "boolean"
                        modifiers = Modifier.PUBLIC or Modifier.STATIC
                        paramTypes = listOf(null)
                        opNames = listOf(
                            "const/4",
                            "if-eqz",
                            "iget",
                            "and-int/lit16",
                            "if-eqz",
                            "iget-object",
                            "if-nez",
                            "sget-object",
                            "iget",
                            "const",
                            "if-ne",
                            "iget-object",
                            "if-nez",
                            "sget-object",
                            "iget",
                            "if-ne",
                            "iget-object",
                            "check-cast",
                            "goto",
                            "sget-object",
                            "goto",
                            "const/4",
                            "if-eqz",
                            "iget-boolean",
                            "if-eqz"
                        )
                    }
                }.single()
            }

        val BackgroundPlaybackSettingsFingerprint =
            getDexMethod("BackgroundPlaybackSettingsBoolean") {
                dexkit.findMethod {
                    matcher {
                        returnType = "java.lang.String"
                        modifiers = Modifier.PUBLIC or Modifier.FINAL
                        paramCount = 0
                        opNames = listOf(
                            "invoke-virtual",
                            "move-result",
                            "invoke-virtual",
                            "move-result",
                            "if-eqz",
                            "if-nez",
                            "goto"
                        )
                        usingNumbers(prefBackgroundAndOfflineCategoryId)
                    }
                }.single().invokes.filter { it.returnTypeName == "boolean" }[1]
            }

        XposedBridge.hookMethod(
            BackgroundPlaybackManagerFingerprint.getMethodInstance(classLoader),
            XC_MethodReplacement.returnConstant(true)
        )
        XposedBridge.hookMethod(
            BackgroundPlaybackSettingsFingerprint.getMethodInstance(classLoader),
            XC_MethodReplacement.returnConstant(true)
        )
    }

    fun RemoveTrackingQueryParameter() {
        val CopyTextFingerprint = getDexMethod("CopyTextFingerprint") {
            dexkit.findMethod {
                matcher {
                    returnType = "void"
                    paramTypes = listOf(null, "java.util.Map")
                    opNames = listOf(
                        "iget-object",
                        "const-string",
                        "invoke-static",
                        "move-result-object",
                        "invoke-virtual",
                        "iget-object",
                        "iget-object",
                        "invoke-interface",
                        "return-void",
                    )
                }
            }.single()
        }

        val sanitizeArg1 = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.args[1] as String
                param.args[1] =
                    url.replace(".si=.+".toRegex(), "").replace(".feature=.+".toRegex(), "")
            }
        }

        XposedBridge.hookMethod(
            CopyTextFingerprint.getMethodInstance(classLoader), ScopedHook(
                XposedHelpers.findMethodExact(
                    ClipData::class.java.name,
                    lpparam.classLoader,
                    "newPlainText",
                    CharSequence::class.java,
                    CharSequence::class.java
                ), sanitizeArg1
            )
        )

        val intentSanitizeHook = ScopedHook(
            XposedHelpers.findMethodExact(
                Intent::class.java.name,
                lpparam.classLoader,
                "putExtra",
                String::class.java,
                String::class.java
            ), sanitizeArg1
        )

        val YouTubeShareSheetFingerprint = getDexMethod("YouTubeShareSheetFingerprint") {
            dexkit.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "void"
                    paramTypes = listOf(null, "java.util.Map")
                    opNames = listOf(
                        "check-cast",
                        "goto",
                        "move-object",
                        "invoke-virtual",
                    )
                    addInvoke("Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;")
                }
            }.single()
        }

        XposedBridge.hookMethod(
            YouTubeShareSheetFingerprint.getMethodInstance(classLoader), intentSanitizeHook
        )

        val SystemShareSheetFingerprint = getDexMethod("SystemShareSheetFingerprint") {
            dexkit.findMethod {
                matcher {
                    returnType = "void"
                    paramTypes = listOf(null, "java.util.Map")
                    opNames = listOf(
                        "check-cast",
                        "goto",
                    )
                    addEqString("YTShare_Logging_Share_Intent_Endpoint_Byte_Array")
                }
            }.single()
        }

        XposedBridge.hookMethod(
            SystemShareSheetFingerprint.getMethodInstance(classLoader), intentSanitizeHook
        )
    }

    fun LithoFilter() {


        //region Pass the buffer into Integrations.
        val ProtobufBufferReferenceFingerprint =
            getDexMethod("ProtobufBufferReferenceFingerprint") {
                dexkit.findMethod {
                    matcher {
                        returnType = "void"
                        modifiers = Modifier.PUBLIC or Modifier.FINAL
                        paramTypes = listOf("int", "java.nio.ByteBuffer")
                        opNames = listOf(
                            "iput", "invoke-virtual", "move-result", "sub-int/2addr"
                        )
                    }
                }.single()
            }

        XposedBridge.hookMethod(ProtobufBufferReferenceFingerprint.getMethodInstance(classLoader),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    LithoFilterPatch.setProtoBuffer(param.args[1] as ByteBuffer)
                }
            })
        //endregion

        //region Hook the method that parses bytes into a ComponentContext.

        val parseBytesToConversionContext = getDexMethod("parseBytesToConversionContext") {
            val method = dexkit.findMethod {
                matcher {
                    usingEqStrings(
                        "Failed to parse Element proto.",
                        "Cannot read theme key from model.",
                        "Number of bits must be positive",
                        "Failed to parse LoggingProperties",
                        "Found an Element with missing debugger id."
                    )
                }
            }.single()
            //
            val conversionContextClass = method.returnType!!
            setString("conversionContextClass", conversionContextClass)
            setString("identifierFieldData",
                conversionContextClass.methods.single { it.methodName == "toString" }.usingFields.filter {
                    it.usingType == FieldUsingType.Read && it.field.typeSign == "Ljava/lang/String;"
                }[1].field
            )
            setString("pathBuilderFieldData",
                conversionContextClass.fields.single { it.typeSign == "Ljava/lang/StringBuilder;" })
            method
        }

        val identifierField = getDexField("identifierFieldData").getFieldInstance(classLoader)
        val pathBuilderField = getDexField("pathBuilderFieldData").getFieldInstance(classLoader)

        val filtered = ThreadLocal<Boolean>()

        XposedBridge.hookMethod(parseBytesToConversionContext.getMethodInstance(classLoader),
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val conversion = param.result

                    val identifier = identifierField.get(conversion) as String
                    val pathBuilder = pathBuilderField.get(conversion) as StringBuilder
                    filtered.set(LithoFilterPatch.filter(identifier, pathBuilder))
                }
            })

        // Return an EmptyComponent instead of the original component if the filterState method returns true.
        val ComponentContextParserFingerprint = getDexMethod("ComponentContextParserFingerprint") {
            dexkit.findMethod {
                matcher {
                    addEqString("Component was not found %s because it was removed due to duplicate converter bindings.")
                }
                matcher {
                    addEqString("Component was not found because it was removed due to duplicate converter bindings.")
                }
            }.single()
        }
        val emptyComponentClass = getDexClass("emptyComponentClass") {
            dexkit.findClass {
                matcher {
                    addMethod {
                        name = "<init>"
                        addEqString("EmptyComponent")
                    }
                }
            }.single()
        }
        val emptyComponentCtor =
            emptyComponentClass.getInstance(classLoader).declaredConstructors.single()

        XposedBridge.hookMethod(ComponentContextParserFingerprint.getMethodInstance(classLoader),
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (filtered.get() == true) {
                        param.result = emptyComponentCtor.newInstance()
                    }
                }
            })
        //endregion
    }

    fun HideAds() {
        // TODO: Hide end screen store banner

        // Hide ad views
        val adAttributionId = getNumber("adAttributionId") {
            app.resources.getIdentifier("ad_attribution", "id", app.packageName)
        }

        XposedHelpers.findAndHookMethod(View::class.java.name,
            lpparam.classLoader,
            "findViewById",
            Int::class.java.name,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.args[0].equals(adAttributionId)) {
                        Logger.printInfo { "Hide Ad Attribution View" }
                        AdsFilter.hideAdAttributionView(param.result as View)
                    }
                }
            })
    }
}
