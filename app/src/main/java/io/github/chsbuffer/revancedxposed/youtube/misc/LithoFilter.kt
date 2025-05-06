package io.github.chsbuffer.revancedxposed.youtube.misc

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

    //region check if the ComponentContext should be filtered, and save the result to a thread local.

    // In 19.17 and earlier, this resolves to the same method as [ComponentContextParserFingerprint],
    // instead of a separate method returns a ConversionContext. In which case,
    // a ScopedHookSafe on `ConversionContext(...)` or `ConversionContextBuilder.build()` is needed,
    // So I don't want to support these versions.
    getDexMethod("readComponentIdentifierFingerprint") {
        findMethod {
            matcher {
                usingEqStrings("Number of bits must be positive")
            }
        }.single().also { method ->
            val conversionContextClass = method.returnType!!
            // Identifier field is the second string type field initialized in the constructor.
            // 0 elementId, 1 identifierProperty
            getDexField("identifierFieldData") {
                conversionContextClass.methods.single {
                    it.isConstructor && it.paramCount != 0
                }.usingFields.filter {
                    it.usingType == FieldUsingType.Write && it.field.typeName == String::class.java.name
                }[1].field
            }
            getDexField("pathBuilderFieldData") {
                conversionContextClass.fields.single { it.typeSign == "Ljava/lang/StringBuilder;" }
            }
        }
    }.hookMethod(object : XC_MethodHook() {
        val identifierField = getDexField("identifierFieldData").toField()
        val pathBuilderField = getDexField("pathBuilderFieldData").toField()
        override fun afterHookedMethod(param: MethodHookParam) {
            val conversion = param.result

            val identifier = identifierField.get(conversion) as String?
            val pathBuilder = pathBuilderField.get(conversion) as StringBuilder
            LithoFilterPatch.filter(identifier, pathBuilder)
        }
    })

    // endregion

    // region return an empty component if filtering is needed.

    // Return an EmptyComponent instead of the original component if the filterState method returns true.
    val emptyComponentClass = getDexClass("emptyComponentClass") {
        findMethod {
            matcher {
                name = "<init>"
                addEqString("EmptyComponent")
            }
        }.single().declaredClass!!
    }

    getDexMethod("ComponentContextParserFingerprint") {
        findMethod {
            matcher {
                strings(
                    "TreeNode result must be set.",
                    // String is a partial match and changed slightly in 20.03+
                    "it was removed due to duplicate converter bindings."
                )
            }
        }.single()
    }.hookMethod(object : XC_MethodHook() {
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
                modifiers = Modifier.PUBLIC or Modifier.STATIC
                paramTypes = listOf(null)
                usingNumbers(45419603L)
            }
        }.single().also { method ->
            getDexMethod("featureFlagCheck") { method.invokes.single() }
        }
    }.hookMethod(
        ScopedHook(
            getDexMethod("featureFlagCheck").toMethod(),
            XC_MethodReplacement.returnConstant(false)
        )
    )

    // endregion
}
