package io.github.chsbuffer.revancedxposed

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.security.JadxSecurityFlag
import jadx.api.security.impl.JadxSecurity
import java.io.Closeable
import java.io.File

class ResourceReader(apkPath: String) : Closeable {
    override fun close() {
        jadx.close()
    }

    val jadx: JadxDecompiler
    operator fun get(type: String, name: String): Int {
        val typeClass = jadx.root.appResClass!!.innerClasses.first { it.name == type }
        val field = typeClass.fields.first { it.name == name }
        return field.get(JadxAttrType.CONSTANT_VALUE).value as Int
    }

    init {
        val jadxArgs = JadxArgs()
        jadxArgs.setInputFile(File(apkPath))
        jadxArgs.security = JadxSecurity(JadxSecurityFlag.none())
        jadx = JadxDecompiler(jadxArgs)
        jadx.load()
    }
}