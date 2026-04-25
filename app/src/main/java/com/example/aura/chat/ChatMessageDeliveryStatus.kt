package com.example.aura.chat

/**
 * Статусы **исходящих** (галочки у пузыря: серая = с моей ноды, две синие = в личке: прочитано
 * собеседником; в канале: хотя бы один собеседник с Aura-Mesh сказал «Прочитано» (квитанция / Data.reply_id).
 * Входящие в БД могут иметь произвольный [code] не для этих пузырей; для пузырей
 * используется [forOutgoingTicks] по полю isOutgoing.
 */
enum class ChatMessageDeliveryStatus(val code: Int) {
    SENDING(0),
    /** Сообщение ушло на ноду/через BLE; одна серая галочка. */
    SENT_TO_NODE(1),
    /** Собеседник подтвердил прочтение (квитанция лички); две синие галочки. */
    READ_IN_PEER_APP(2),
    FAILED(3),
    /**
     * Старые сессии: «первый» ROUTING mesh; в UI = как [SENT_TO_NODE].
     */
    DELIVERED_TO_NODE(4),
    ;

    fun forOutgoingIcon(): ChatMessageDeliveryStatus = when (this) {
        DELIVERED_TO_NODE -> SENT_TO_NODE
        else -> this
    }

    companion object {
        fun fromCode(code: Int): ChatMessageDeliveryStatus =
            ChatMessageDeliveryStatus.entries.find { it.code == code } ?: SENDING

        /**
         * Для рисования [OutgoingDeliveryTicks] на исходящих: код 4 = серая, как 1;
         * код 2 = две синие (read receipt).
         */
        fun forOutgoingTicks(code: Int): ChatMessageDeliveryStatus =
            fromCode(code).forOutgoingIcon()
    }
}
