package io.github.chsbuffer.revancedxposed.youtube.misc

import android.annotation.SuppressLint
import app.revanced.extension.youtube.patches.components.LithoFilterPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.ScopedHook
import io.github.chsbuffer.revancedxposed.new
import io.github.chsbuffer.revancedxposed.opcodes
import io.github.chsbuffer.revancedxposed.strings
import io.github.chsbuffer.revancedxposed.youtube.YoutubeHook
import org.luckypray.dexkit.result.FieldUsingType
import java.lang.reflect.Modifier
import java.nio.ByteBuffer


@SuppressLint("NonUniqueDexKitData")
fun YoutubeHook.LithoFilter() {

    //region Pass the buffer into extension.
    getDexMethod("ProtobufBufferReferenceFingerprint") {
        findMethod {
            matcher {
                returnType = "void"
                modifiers = Modifier.PUBLIC or Modifier.FINAL
                paramTypes = listOf("int", "java.nio.ByteBuffer")
                opcodes(
                    Opcode.IPUT,
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.MOVE_RESULT,
                    Opcode.SUB_INT_2ADDR,
                )
            }
        }.single()
    }.hookMethod(object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            LithoFilterPatch.setProtoBuffer(param.args[1] as ByteBuffer)
        }
    })

    //endregion

    // region Hook the method that parses bytes into a ComponentContext.
    val conversionContextClass = getDexClass("conversionContextClass") {
        findClass {
            matcher {
                usingStrings("ConversionContext{")
            }
        }.single().also { clazz ->
            // Identifier field is the second string type field initialized in the constructor.
            // 0 elementId, 1 identifierProperty
            getDexField("identifierFieldData") {
                clazz.methods.single {
                    it.isConstructor && it.paramCount != 0
                }.usingFields.filter {
                    it.usingType == FieldUsingType.Write && it.field.typeName == String::class.java.name
                }[1].field
            }
            getDexField("pathBuilderFieldData") {
                clazz.fields.single { it.typeSign == "Ljava/lang/StringBuilder;" }
            }
        }
    }

    val componentContextParserMethod = getDexMethod("ComponentContextParserFingerprint") {
        findMethod {
            matcher {
                // String is a partial match and changed slightly in 20.03+
                strings("it was removed due to duplicate converter bindings.")
            }
        }.single()
    }

    val componentContextSubParser = getDexMethod("ComponentContextSubParserFingerprint") {
        getMethodData(componentContextParserMethod.toString())!!.invokes.first {
            it.returnTypeName == conversionContextClass.typeName
        }
    }

    // Return an EmptyComponent instead of the original component if the filterState method returns true.
    val emptyComponentClass = getDexClass("emptyComponentClass") {
        findMethod {
            matcher {
                name = "<init>"
                addEqString("EmptyComponent")
            }
        }.single().declaredClass!!
    }

    // check if the ComponentContext should be filtered, and save the result to a thread local.
    componentContextParserMethod.hookMethod(ScopedHook(componentContextSubParser.toMethod()) {
        val identifierField = getDexField("identifierFieldData").toField()
        val pathBuilderField = getDexField("pathBuilderFieldData").toField()
        after {
            val conversion = param.result
            val identifier = identifierField.get(conversion) as String?
            val pathBuilder = pathBuilderField.get(conversion) as StringBuilder
            LithoFilterPatch.filter(identifier, pathBuilder)
        }
    })

    // return an empty component if filtering is needed.
    componentContextParserMethod.hookMethod(object : XC_MethodHook(PRIORITY_DEFAULT - 1) {
        val emptyComponentClazz = emptyComponentClass.toClass()
        override fun afterHookedMethod(param: MethodHookParam) {
            if (LithoFilterPatch.shouldFilter()) {
                param.result = emptyComponentClazz.new()
            }
        }
    })

    //endregion

    // region A/B test of new Litho native code.

    // Turn off native code that handles litho component names.  If this feature is on then nearly
    // all litho components have a null name and identifier/path filtering is completely broken.
    //
    // Flag was removed in 20.05. It appears a new flag might be used instead (45660109L),
    // but if the flag is forced on then litho filtering still works correctly.
    runCatching {
        getDexMethod("lithoComponentNameUpbFeatureFlagFingerprint") {
            findMethod {
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    returnType = "boolean"
                    paramTypes = listOf()
                    usingNumbers(45631264L)
                }
            }.single()
        }.hookMethod(XC_MethodReplacement.returnConstant(false))
    }

    // Turn off a feature flag that enables native code of protobuf parsing (Upb protobuf).
    // If this is enabled, then the litho protobuffer hook will always show an empty buffer
    // since it's no longer handled by the hooked Java code.
    getDexMethod("lithoConverterBufferUpbFeatureFlagFingerprint") {
        findMethod {
            matcher {
                usingNumbers(45419603L)
            }
        }.single().also { method ->
            getDexMethod("featureFlagCheck") {
                method.invokes.findMethod {
                    matcher {
                        returnType = "boolean"
                        paramTypes("long", "boolean")
                    }
                }.single()
            }
        }
    }.hookMethod(
        ScopedHook(getDexMethod("featureFlagCheck").toMethod()) {
            returnConstant(false)
        }
    )

    // endregion
}
