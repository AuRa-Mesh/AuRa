package com.example.aura.data.local

/** Тип записи в унифицированной истории сообщений. */
enum class MessageHistoryType(val code: Int) {
    TEXT(0),
    VOICE(1),
    IMAGE(2),
    COORDINATES(3),
    SERVICE(4),
    ;

    companion object {
        fun fromCode(code: Int): MessageHistoryType =
            entries.find { it.code == code } ?: TEXT
    }
}
