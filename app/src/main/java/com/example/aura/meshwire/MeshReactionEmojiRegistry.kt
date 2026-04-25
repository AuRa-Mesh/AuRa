package com.example.aura.meshwire

/**
 * Компактный маппинг реакций для LoRa: **1 байт (1..255)** → строка эмодзи.
 * Порядок совпадает с панелью реакций в чате; [ALL_EMOJIS] не длиннее [MAX_WIRE_ID].
 *
 * **Формат по эфиру (tapback mesh, portnum TEXT):**
 * - **1 байт** — только wire ID (добавить реакцию, legacy);
 * - **2 байта:** байт 0 = wire ID (1..255), байт 1 = флаг **добавить (≠0) / снять (=0)** — второй байт кодирует один бит «isAdding»;
 * иначе — UTF-8 эмодзи (совместимость со старыми клиентами).
 */
object MeshReactionEmojiRegistry {

    const val MAX_WIRE_ID: Int = 255

    /** Первые [QUICK_EMOJI_COUNT] — «быстрые» в меню. */
    const val QUICK_EMOJI_COUNT: Int = 7

    val ALL_EMOJIS: List<String> = buildList {
        addAll(
            listOf("👍", "❤️", "🫡", "🔥", "😭", "😍", "👌"),
        )
        addAll(
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "🙂", "😉", "😊", "😇", "🥰", "😘", "😋", "😛", "😜",
                "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
                "🤥", "😌", "😔", "😪", "🤤", "😴", "😎", "🤓", "🧐", "😕", "😟", "🙁", "☹️", "😮", "😯", "😲",
                "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱",
                "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "💋", "💌", "💘", "💖", "💗", "💓",
                "💞", "💕", "💟", "❣️", "💔", "❤️‍🔥", "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍", "💯", "💢",
                "💥", "💫", "💦", "💨", "💣", "💬", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪",
                "🎉", "✨", "⭐️", "⚡️", "🌈", "☀️", "🌙", "☁️", "⛈️", "❄️", "🍀", "🌹", "🥀", "🌸", "🍕", "🍔",
                "🍟", "🌮", "🍰", "🎂", "🍫", "☕️", "🎯", "🎮", "🎲", "🎧", "🎤", "🎵", "🎹", "🎸", "🪐", "🌟",
                "⭐", "✅", "❌", "❓", "❗️", "🙈", "🙉", "🙊", "💤", "👀", "🔥", "🫶", "🫰", "🫵", "🫠", "🤌",
                "🤏",
            ),
        )
    }.distinct()

    init {
        require(ALL_EMOJIS.size <= MAX_WIRE_ID) {
            "Слишком много реакций для 1 байта: ${ALL_EMOJIS.size} > $MAX_WIRE_ID"
        }
    }

    /** Wire ID 1..N, где N = [ALL_EMOJIS].size; 0 — недопустим. */
    fun emojiForWireId(wireId: Int): String? {
        if (wireId !in 1..MAX_WIRE_ID) return null
        val idx = wireId - 1
        return ALL_EMOJIS.getOrNull(idx)
    }

    fun wireIdForListIndex(listIndex: Int): Int? {
        if (listIndex !in ALL_EMOJIS.indices) return null
        return listIndex + 1
    }

    fun emojiAtListIndex(listIndex: Int): String? = ALL_EMOJIS.getOrNull(listIndex)

    /** Индекс в [ALL_EMOJIS] для строки эмодзи из чипа. */
    fun listIndexForEmoji(emoji: String): Int? {
        val i = ALL_EMOJIS.indexOf(emoji.trim())
        return i.takeIf { it >= 0 }
    }
}
