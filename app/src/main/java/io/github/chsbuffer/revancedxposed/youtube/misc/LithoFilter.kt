package io.github.chsbuffer.revancedxposed.youtube.misc

import app.revanced.extension.youtube.patches.components.LithoFilterPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Modifier
import java.nio.ByteBuffer


fun YoutubeHook.LithoFilter() {

    //region Pass the buffer into extension.
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

                val identifier = identifierField.get(conversion) as String?
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

    // region A/B test of new Litho native code.

    // Turn off native code that handles litho component names.  If this feature is on then nearly
    // all litho components have a null name and identifier/path filtering is completely broken.
    runCatching {
        getDexMethod("lithoComponentNameUpbFeatureFlagFingerprint") {
            dexkit.findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "boolean"
                    paramTypes = listOf()
                    usingNumbers(45631264L)
                }
            }.single()
        }
    }.onFailure {
        // ignored
    }

    // Turn off a feature flag that enables native code of protobuf parsing (Upb protobuf).
    // If this is enabled, then the litho protobuffer hook will always show an empty buffer
    // since it's no longer handled by the hooked Java code.
    getDexMethod("lithoConverterBufferUpbFeatureFlagFingerprint") {
        dexkit.findMethod {
            matcher {
                modifiers = Modifier.PUBLIC or Modifier.STATIC
                paramTypes = listOf(null)
                usingNumbers(45419603L)
            }
        }.single().apply {
            getDexMethod("featureFlagCheck") {
                this.invokes.single()
            }
        }
    }.apply {
        val featureFlagCheckMethod =
            getDexMethod("featureFlagCheck").getMethodInstance(classLoader)
        XposedBridge.hookMethod(
            getMethodInstance(classLoader),
            ScopedHook(featureFlagCheckMethod, XC_MethodReplacement.returnConstant(false))
        )
    }
    // endregion
}
