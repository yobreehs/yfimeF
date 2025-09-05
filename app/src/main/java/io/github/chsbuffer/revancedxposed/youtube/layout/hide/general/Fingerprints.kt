package io.github.chsbuffer.revancedxposed.youtube.layout.hide.general

import io.github.chsbuffer.revancedxposed.AccessFlags
import io.github.chsbuffer.revancedxposed.Opcode
import io.github.chsbuffer.revancedxposed.findFieldDirect
import io.github.chsbuffer.revancedxposed.findMethodDirect
import io.github.chsbuffer.revancedxposed.fingerprint
import io.github.chsbuffer.revancedxposed.resourceMappings

val expandButtonDownId get() = resourceMappings["layout", "expand_button_down"]

val albumCardId get() = resourceMappings["layout", "album_card"]

val crowdfundingBoxId get() = resourceMappings["layout", "donation_companion"]

val youTubeLogo get() = resourceMappings["id", "youtube_logo"]

val relatedChipCloudMarginId get() = resourceMappings["layout", "related_chip_cloud_reduced_margins"]

val filterBarHeightId get() = resourceMappings["dimen", "filter_bar_height"]

val barContainerHeightId get() = resourceMappings["dimen", "bar_container_height"]

val fabButtonId get() = resourceMappings["id", "fab"]

internal val parseElementFromBufferFingerprint = fingerprint {
    parameters("L", "L", "[B", "L", "L")
    strings("Failed to parse Element") // String is a partial match.
}

internal val conversionContextField = findFieldDirect {
    val parseElementFromBufferFingerprint = parseElementFromBufferFingerprint()
    val conversionContextInterface = parseElementFromBufferFingerprint.invokes.single {
        it.paramTypeNames.firstOrNull() == "byte[]"
    }.declaredClass!!
    parseElementFromBufferFingerprint.usingFields.distinct().single { it.field.typeSign == conversionContextInterface.descriptor }.field
}

internal val playerOverlayFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("L")
    strings("player_overlay_in_video_programming")
}

internal val showWatermarkFingerprint = fingerprint {
    classMatcher { className(playerOverlayFingerprint(dexkit).declaredClassName) }
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    parameters("L", "L")
}

internal val showWatermarkSubFingerprint = findMethodDirect {
    showWatermarkFingerprint().invokes.findMethod {
        matcher {
            returnType = "void"
            paramTypes("android.view.View", "boolean")
        }
    }.single()
}

/**
 * Matches same method as [wideSearchbarLayoutFingerprint].
 */
internal val yoodlesImageViewFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("Landroid/view/View;")
    parameters("L", "L")
    literal { youTubeLogo }
}

internal val crowdfundingBoxFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    opcodes(
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.IPUT_OBJECT,
    )
    literal { crowdfundingBoxId }
}

internal val albumCardsFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    opcodes(
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CONST,
        Opcode.CONST_4,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.CHECK_CAST,
    )
    literal { albumCardId }
}

internal val filterBarHeightFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    returns("V")
    opcodes(
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.IPUT,
    )
    literal { filterBarHeightId }
}

internal val relatedChipCloudFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    returns("V")
    opcodes(
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
    )
    literal { relatedChipCloudMarginId }
}

internal val searchResultsChipBarFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    returns("V")
    opcodes(
        Opcode.CONST,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT,
        Opcode.INVOKE_VIRTUAL,
        Opcode.MOVE_RESULT_OBJECT,
    )
    literal { barContainerHeightId }
}

internal val showFloatingMicrophoneButtonFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    returns("V")
    parameters()
    opcodes(
        Opcode.IGET_BOOLEAN,
        Opcode.IF_EQZ,
    )
    literal { fabButtonId }
}
