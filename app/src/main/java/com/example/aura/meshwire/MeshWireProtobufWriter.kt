package com.example.aura.meshwire

import java.io.ByteArrayOutputStream

/** Мини-сериализатор protobuf3 (достаточно для ToRadio + Admin + LoRa). */
internal class MeshWireProtobufWriter {
    private val buf = ByteArrayOutputStream(128)

    fun toByteArray(): ByteArray = buf.toByteArray()

    fun writeFixed32Field(fieldNumber: Int, value: UInt) {
        writeTag(fieldNumber, 5)
        val v = value.toInt()
        buf.write(v and 0xFF)
        buf.write((v shr 8) and 0xFF)
        buf.write((v shr 16) and 0xFF)
        buf.write((v shr 24) and 0xFF)
    }

    fun writeFloatField(fieldNumber: Int, value: Float) {
        writeTag(fieldNumber, 5)
        val bits = java.lang.Float.floatToRawIntBits(value)
        buf.write(bits and 0xFF)
        buf.write((bits shr 8) and 0xFF)
        buf.write((bits shr 16) and 0xFF)
        buf.write((bits shr 24) and 0xFF)
    }

    fun writeBoolField(fieldNumber: Int, value: Boolean) {
        writeTag(fieldNumber, 0)
        writeVarintUInt(if (value) 1U else 0U)
    }

    fun writeUInt32Field(fieldNumber: Int, value: UInt) {
        writeTag(fieldNumber, 0)
        writeVarintUInt(value)
    }

    fun writeInt32Field(fieldNumber: Int, value: Int) {
        writeTag(fieldNumber, 0)
        if (value >= 0) {
            writeVarintUInt(value.toUInt())
        } else {
            writeVarintLong(value.toLong())
        }
    }

    /** Protobuf `int64` / `uint64` как varint (положительные значения — компактно). */
    fun writeInt64Field(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, 0)
        writeVarintLong(value)
    }

    fun writeEnumField(fieldNumber: Int, value: Int) {
        writeUInt32Field(fieldNumber, value.toUInt())
    }

    fun writeLengthDelimitedField(fieldNumber: Int, payload: ByteArray) {
        writeTag(fieldNumber, 2)
        writeVarintUInt(payload.size.toUInt())
        buf.write(payload, 0, payload.size)
    }

    fun writeStringField(fieldNumber: Int, value: String) {
        writeLengthDelimitedField(fieldNumber, value.toByteArray(Charsets.UTF_8))
    }

    fun writeEmbeddedMessage(fieldNumber: Int, buildInner: MeshWireProtobufWriter.() -> Unit) {
        val inner = MeshWireProtobufWriter()
        inner.buildInner()
        writeLengthDelimitedField(fieldNumber, inner.toByteArray())
    }

    private fun writeTag(fieldNumber: Int, wireType: Int) {
        writeVarintUInt(((fieldNumber.toUInt() shl 3) or wireType.toUInt()))
    }

    private fun writeVarintUInt(vIn: UInt) {
        var v = vIn
        while (v >= 0x80U) {
            buf.write((v.toInt() and 0x7F) or 0x80)
            v = v shr 7
        }
        buf.write(v.toInt() and 0x7F)
    }

    private fun writeVarintLong(vIn: Long) {
        var v = vIn
        while (v and 0xFFFFFF80.toLong().inv() != 0L) {
            buf.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        buf.write(v.toInt() and 0x7F)
    }
}
