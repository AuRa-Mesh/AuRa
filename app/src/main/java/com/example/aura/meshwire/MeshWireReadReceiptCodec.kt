package com.example.aura.meshwire

/**
 * Квитанция «собеседник прочитал в Aura-Mesh» для лички.
 * Тело в UTF-8 — [READ_RECEIPT_WIRE_BODY]; id пакета исходного сообщения — [Data.reply_id]
 * (см. [MeshWireLoRaToRadioEncoder.encodeTextReplyMessageToRadioWithId]).
 */
object MeshWireReadReceiptCodec {
    /**
     * Короткое тело payload; в списке чата у исходящей строки не показываем (см. [shouldHideReadReceiptInChatList]).
     */
    const val READ_RECEIPT_WIRE_BODY = "Прочитано"

    fun readReceiptWireText(): String = READ_RECEIPT_WIRE_BODY

    /**
     * @return id пакета, отмеченного как прочитанный, либо null, если формат не квитанция.
     * Для [READ_RECEIPT_WIRE_BODY] нужен [dataReplyId] (mesh reply_id).
     */
    fun parseReadReceiptTargetPacketId(utf8Text: String, dataReplyId: UInt? = null): Long? {
        if (utf8Text.trim() != READ_RECEIPT_WIRE_BODY) return null
        val r = dataReplyId ?: return null
        return r.toLong() and 0xFFFF_FFFFL
    }

    /**
     * Не отображаем в ленте исходящую строку с телом квитанции (есть [replyToPacketId]).
     */
    fun shouldHideReadReceiptInChatList(
        text: String,
        isOutgoing: Boolean,
        replyToPacketId: UInt? = null,
    ): Boolean = isOutgoing &&
        text.trim() == READ_RECEIPT_WIRE_BODY &&
        replyToPacketId != null
}
