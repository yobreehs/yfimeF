package io.github.chsbuffer.revancedxposed.spotify.misc

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.SkipTest
import io.github.chsbuffer.revancedxposed.findClassDirect
import io.github.chsbuffer.revancedxposed.findFieldDirect
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.strings
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.enums.UsingType
import org.luckypray.dexkit.result.FieldData
import java.lang.reflect.Modifier

val productStateProtoFingerprint = fingerprint {
    returns("Ljava/util/Map;")
    classMatcher { descriptor = "Lcom/spotify/remoteconfig/internal/ProductStateProto;" }
}

val attributesMapField =
    findFieldDirect { productStateProtoFingerprint().usingFields.single().field }

val buildQueryParametersFingerprint = findMethodDirect {
    findMethod {
        matcher {
            strings("trackRows", "device_type:tablet")
        }
    }.single()
}

/**
 * The Google Assistant player-Context deserializer.
 *
 * Up to ~9.1.86 this was `…playermodels.ContextJsonAdapter.fromJson(JsonReader):Context`.
 * Since ~9.1.90 the class was renamed to `…playermodels.ContextSerializer` and the method to
 * `deserialize`; both still return `com.spotify.player.model.Context`, which the hook
 * post-processes to strip `spotify:station:` URIs. The old opcode-based matcher is kept as a
 * fallback so the patch survives on older app versions too.
 */
val contextFromJsonFingerprint = findMethodDirect {
    runCatching {
        fingerprint {
            returns("Lcom/spotify/player/model/Context;")
            methodMatcher {
                name("deserialize")
                declaredClass(
                    "voiceassistants.playermodels.ContextSerializer", StringMatchType.EndsWith
                )
            }
        }
    }.getOrElse {
        fingerprint {
            opcodes(
                Opcode.INVOKE_STATIC,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_VIRTUAL,
                Opcode.MOVE_RESULT_OBJECT,
                Opcode.INVOKE_STATIC
            )
            methodMatcher {
                name("fromJson")
                declaredClass(
                    "voiceassistants.playermodels.ContextJsonAdapter", StringMatchType.EndsWith
                )
            }
        }
    }
}

val contextMenuViewModelClass = findClassDirect {
    return@findClassDirect runCatching {
        fingerprint {
            strings("ContextMenuViewModel(header=")
        }
    }.getOrElse {
        fingerprint {
            accessFlags(AccessFlags.CONSTRUCTOR)
            strings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")
            parameters("L", "Ljava/util/List;", "Z")
        }
    }.declaredClass!!
}

val contextMenuPremiumItemResIdField = findFieldDirect {
    // The ViewModel ctor reads each item's resId directly from a String field.
    val ctor = contextMenuViewModelClass().methods.first { m ->
        m.isConstructor && m.paramTypeNames.contains("java.util.List") && m.usingFields.any {
            it.usingType == org.luckypray.dexkit.result.FieldUsingType.Read &&
                it.field.name == "a" && it.field.typeName == "java.lang.String"
        }
    }
    ctor.usingFields.first { uf ->
        uf.usingType == org.luckypray.dexkit.result.FieldUsingType.Read &&
            uf.field.name == "a" && uf.field.typeName == "java.lang.String"
    }.field
}

val contextMenuItemInterface = findClassDirect {
    contextMenuPremiumItemResIdField().declaredClass!!
}

@SkipTest
fun structureGetSectionsFingerprint(className: String) = fingerprint {
    classMatcher { className(className, StringMatchType.EndsWith) }
    methodMatcher {
        addUsingField {
            usingType = UsingType.Read
            name = "sections_"
        }
    }
}

val homeStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("casita.v1.resolved.HomeStructure")
val browseStructureGetSectionsFingerprint =
    structureGetSectionsFingerprint("browsita.v1.resolved.BrowseStructure")

/**
 * Sections of the Now Playing View scroll feed. Each section maps to a card (lyrics, merch, …);
 * a section carrying `IMAGE_BRAND_AD_FIELD_NUMBER` is the embedded ad banner (the row shown
 * under the lyrics card).
 *
 * Unlike Home/Browse, `NpvScrollStructure` also has an `addSection(Section)` method that reads
 * the backing `sections_` field, so the getter is pinned by its return type.
 */
val npvScrollStructureGetSectionsFingerprint = findMethodDirect {
    findMethod {
        matcher {
            declaredClass("scrollsita.v1.NpvScrollStructure", StringMatchType.EndsWith)
            addUsingField {
                usingType = UsingType.Read
                name = "sections_"
            }
            paramTypes(emptyList())
        }
    }.first { it.returnTypeName != "void" }
}

val pendragonJsonFetchMessageRequestFingerprint = findMethodDirect {
    findMethod {
        matcher {
            name("apply")
            addInvoke {
                name("<init>")
                declaredClass("FetchMessageRequest", StringMatchType.EndsWith)
            }
        }
    }.single()
}

val pendragonJsonFetchMessageListRequestFingerprint = findMethodDirect {
    findMethod {
        matcher {
            name("apply")
            addInvoke {
                name("<init>")
                declaredClass("FetchMessageListRequest", StringMatchType.EndsWith)
            }
        }
    }.single()
}

private const val SETTINGS_STATE_SERIAL_NAME = "com.spotify.settings.rxsettings.SettingsState"

/**
 * Locates the boolean field of the settings state class through its generated
 * kotlinx.serialization serializer.
 *
 * The state class is `@Serializable`, so Spotify ships a generated serializer for it: a class
 * that registers the serial name (`com.spotify.settings.rxsettings.SettingsState`) together with
 * every element name (`offline_mode`, `show_unavailable_tracks`, …) in its static initializer and
 * whose `deserialize` method instantiates the (obfuscated) state class from those elements. The
 * element order of the serializer is the constructor parameter order, which in turn is the field
 * declaration order of the state class, so the requested element maps to the field with the same
 * index.
 *
 * This survives obfuscation because serial names are string literals, and it does not depend on
 * the generated Kotlin property references that older app versions also shipped.
 */
private fun DexKitBridge.settingsStateFieldBySerializer(elementName: String): FieldData {
    val serializerClass = findMethod { matcher { strings(elementName) } }
        .mapNotNull { it.declaredClass }
        .distinct()
        .firstOrNull { candidate ->
            candidate.methods.any { method ->
                method.invokes.any { it.isConstructor && it.paramCount >= 10 }
            }
        } ?: error("SettingsState serializer not found")

    val descriptorInit = serializerClass.methods.first { it.usingStrings.contains(elementName) }
    val strings = descriptorInit.usingStrings
    val serialNameIndex = strings.indexOf(SETTINGS_STATE_SERIAL_NAME)
    require(serialNameIndex >= 0) { "Serial name not found in ${serializerClass.descriptor}" }
    val elementIndex = strings.indexOf(elementName) - serialNameIndex - 1
    require(elementIndex >= 0) { "Element $elementName not found in ${serializerClass.descriptor}" }

    val stateConstructor = serializerClass.methods
        .flatMap { it.invokes }
        .filter { it.isConstructor && it.paramCount >= 10 }
        .maxByOrNull { it.paramCount }
        ?: error("SettingsState constructor not found in ${serializerClass.descriptor}")
    val stateClass = stateConstructor.declaredClass
        ?: error("SettingsState class not found in ${stateConstructor.descriptor}")

    val fields = stateClass.fields.filter { !Modifier.isStatic(it.modifiers) }
    val field = fields.getOrNull(elementIndex)
        ?: error("No field #$elementIndex in ${stateClass.descriptor}")
    require(field.typeName == "boolean") {
        "Field ${field.descriptor} for $elementName is not boolean"
    }
    return field
}

/**
 * Legacy lookup through the generated `…showUnavailableTracks` property reference: the property
 * reference class is the one that both mentions the literal `showUnavailableTracks()Z` and has
 * the `get(Object):Object` accessor that reads the field (`cd31.o` on 9.1.90). Those property
 * references disappeared in Spotify 9.1.92.
 */
private fun DexKitBridge.settingsStateFieldByPropertyReference(propertyName: String): FieldData {
    val propertyReferenceCandidates = findMethod {
        matcher { strings("${propertyName}()Z") }
    }.mapNotNull { it.declaredClass }

    val propertyReferenceClass = propertyReferenceCandidates.first { candidate ->
        runCatching {
            fingerprint {
                definingClass(candidate.descriptor)
                returns("Ljava/lang/Object;")
                parameters("Ljava/lang/Object;")
                methodMatcher { name("get") }
            }
        }.isSuccess
    }

    return fingerprint {
        definingClass(propertyReferenceClass.descriptor)
        returns("Ljava/lang/Object;")
        parameters("Ljava/lang/Object;")
        methodMatcher { name("get") }
    }.usingFields.single().field
}

/**
 * The boolean field backing Spotify's own "Show unplayable songs" setting.
 *
 * The setting lives in the (server-synced) settings state
 * `com.spotify.settings.rxsettings.SettingsState` under the key `show_unavailable_tracks`, but
 * that class is obfuscated and its consumers read the field directly.
 */
val showUnavailableTracksField = findFieldDirect {
    runCatching { settingsStateFieldBySerializer("show_unavailable_tracks") }
        .recoverCatching { settingsStateFieldByPropertyReference("showUnavailableTracks") }
        .getOrThrow()
}
