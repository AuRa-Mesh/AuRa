package com.example.aura.data.local

import org.json.JSONArray
import org.json.JSONObject

/** Одна реакция на сообщение (хранится в JSON-массиве в [ChannelChatMessageEntity.reactionsJson]). */
data class ReactionItem(
    val emoji: String,
    val senderId: String,
    val timestamp: Long,
)

/** Группа для UI: эмодзи, число, участвует ли текущий пользователь. */
data class GroupedReactionUi(
    val emoji: String,
    val count: Int,
    val includesMine: Boolean,
)

/**
 * JSON-массив [ReactionItem] в колонке [ChannelChatMessageEntity.reactionsJson].
 *
 * Правила: у одного [senderId] не более [MAX_DISTINCT_EMOJIS_PER_SENDER] различных типов эмодзи; при превышении удаляется самая старая реакция этого отправителя; повтор того же эмодзи — **снятие** (toggle).
 * На сообщении не более [MAX_DISTINCT_EMOJI_TYPES_ON_MESSAGE] различных типов эмодзи (глобально); при превышении удаляется **самая старая** запись по [timestamp].
 */
object ChatMessageReactionsJson {

    const val EMPTY_ARRAY: String = "[]"

    /** Сколько разных эмодзи может поставить один пользователь на одно сообщение (сверх лимита удаляется самая старая). */
    const val MAX_DISTINCT_EMOJIS_PER_SENDER: Int = 2

    /** Сколько разных типов эмодзи допускается на сообщении (все участники вместе). */
    const val MAX_DISTINCT_EMOJI_TYPES_ON_MESSAGE: Int = 32

    fun parseList(json: String?): List<ReactionItem> {
        val raw = json?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        if (raw == "{}") return emptyList()
        if (raw.startsWith("{")) return migrateFromSenderMap(JSONObject(raw))
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val emoji = o.optString("emoji", "").trim()
                    val senderId = o.optString("senderId", "").trim()
                    val ts = o.optLong("timestamp", System.currentTimeMillis())
                    if (emoji.isNotEmpty() && senderId.isNotEmpty()) {
                        add(ReactionItem(emoji = emoji, senderId = senderId, timestamp = ts))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun migrateFromSenderMap(o: JSONObject): List<ReactionItem> {
        val now = System.currentTimeMillis()
        return buildList {
            val it = o.keys()
            while (it.hasNext()) {
                val senderId = it.next().trim()
                val emoji = o.optString(senderId, "").trim()
                if (senderId.isNotEmpty() && emoji.isNotEmpty()) {
                    add(ReactionItem(emoji = emoji, senderId = senderId, timestamp = now))
                }
            }
        }
    }

    fun serialize(items: List<ReactionItem>): String {
        val arr = JSONArray()
        for (it in items) {
            arr.put(
                JSONObject().apply {
                    put("emoji", it.emoji)
                    put("senderId", it.senderId)
                    put("timestamp", it.timestamp)
                },
            )
        }
        return arr.toString()
    }

    /** Toggle: тот же [senderId]+[emoji] — удалить; иначе добавить и применить лимиты. */
    fun applyTapOrToggle(
        existingJson: String?,
        senderId: String,
        emoji: String,
        atMillis: Long,
    ): String {
        val sid = senderId.trim()
        val em = emoji.trim()
        if (sid.isEmpty() || em.isEmpty()) return serialize(parseList(existingJson))
        val items = parseList(existingJson).toMutableList()
        val idx = items.indexOfFirst { it.senderId == sid && it.emoji == em }
        if (idx >= 0) {
            items.removeAt(idx)
        } else {
            items.add(ReactionItem(emoji = em, senderId = sid, timestamp = atMillis))
            enforcePerSenderMaxDistinctEmojis(items, sid, maxDistinct = MAX_DISTINCT_EMOJIS_PER_SENDER)
            enforceGlobalMaxDistinctEmojis(items, maxDistinctEmojiTypes = MAX_DISTINCT_EMOJI_TYPES_ON_MESSAGE)
        }
        return serialize(items)
    }

    fun removeAllFromSender(existingJson: String?, senderId: String): String {
        val sid = senderId.trim()
        if (sid.isEmpty()) return serialize(parseList(existingJson))
        val items = parseList(existingJson).filterNot { it.senderId == sid }
        return serialize(items)
    }

    /** Снять у [senderId] реакцию [emoji], если была (без toggle-добавления). */
    fun removeSenderEmojiIfPresent(existingJson: String?, senderId: String, emoji: String): String {
        val sid = senderId.trim()
        val em = emoji.trim()
        if (sid.isEmpty() || em.isEmpty()) return serialize(parseList(existingJson))
        val items = parseList(existingJson).toMutableList()
        items.removeAll { it.senderId == sid && it.emoji == em }
        return serialize(items)
    }

    private fun enforcePerSenderMaxDistinctEmojis(items: MutableList<ReactionItem>, senderId: String, maxDistinct: Int) {
        while (true) {
            val mine = items.filter { it.senderId == senderId }
            val distinct = mine.map { it.emoji }.distinct()
            if (distinct.size <= maxDistinct) break
            val victim = mine.minByOrNull { it.timestamp } ?: break
            val i = items.indexOfFirst {
                it.senderId == victim.senderId && it.emoji == victim.emoji && it.timestamp == victim.timestamp
            }
            if (i < 0) break
            items.removeAt(i)
        }
    }

    private fun enforceGlobalMaxDistinctEmojis(items: MutableList<ReactionItem>, maxDistinctEmojiTypes: Int) {
        while (true) {
            val distinct = items.map { it.emoji }.distinct()
            if (distinct.size <= maxDistinctEmojiTypes) break
            val victim = items.minByOrNull { it.timestamp } ?: break
            val i = items.indexOfFirst {
                it.senderId == victim.senderId && it.emoji == victim.emoji && it.timestamp == victim.timestamp
            }
            if (i < 0) break
            items.removeAt(i)
        }
    }

    fun groupedCounts(items: List<ReactionItem>): List<Pair<String, Int>> =
        items
            .groupingBy { it.emoji }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    fun reactionPresentationChips(reactionsJson: String, mySenderId: String?): List<GroupedReactionUi> {
        val items = parseList(reactionsJson)
        val byEmoji = items.groupBy { it.emoji }
        return byEmoji.entries
            .map { (emoji, list) ->
                val count = list.size
                val includesMine = mySenderId != null && list.any { it.senderId == mySenderId }
                GroupedReactionUi(emoji = emoji, count = count, includesMine = includesMine)
            }
            .sortedWith(compareByDescending<GroupedReactionUi> { it.count }.thenBy { it.emoji })
    }
}

/** Группировка по эмодзи с сортировкой по популярности (как в Telegram). */
fun ChannelChatMessageEntity.getGroupedReactions(): List<Pair<String, Int>> =
    ChatMessageReactionsJson.groupedCounts(ChatMessageReactionsJson.parseList(reactionsJson))
