package com.example.aura.security

import android.content.Context
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.vip.VipUsedCodeMeshAnnouncer

/**
 * Применение одноразового VIP-кода продления.
 *
 * Проверки:
 *  1. Формат: 12 base32-символов (с/без дефисов и пробелов).
 *  2. HMAC: подпись соответствует [nodeId] (код, выданный на другой узел, отвергается).
 *  3. Одноразовость: код ещё не был предъявлен (см. [VipExtensionUsedCodes]).
 *
 * При успехе вызывает [VipAccessPreferences.extendByDays] и помечает код использованным.
 */
object VipExtensionCodeRedeemer {

    sealed interface RedeemResult {
        data class Success(val daysApplied: Int, val newDeadlineMs: Long) : RedeemResult
        /** Ввод пустой, слишком длинный/короткий, неверные символы. */
        data object InvalidFormat : RedeemResult
        /** Подпись не прошла: код рассчитан для другого узла или подделан. */
        data object WrongNodeOrTampered : RedeemResult
        /** Код корректен, но ранее уже предъявлялся на этом устройстве/аккаунте. */
        data object AlreadyUsed : RedeemResult
    }

    /**
     * Попытаться применить [input] как код продления для [nodeId].
     *
     * `nodeId` — уже авторизованный в приложении идентификатор узла ([NodeAuthStore]).
     */
    fun redeem(context: Context, nodeId: String, input: String): RedeemResult {
        val decoded = VipExtensionCodeCodec.decodeAndVerify(nodeId, input) ?: run {
            val normalized = VipExtensionCodeCodec.normalizeCodeInput(input)
            return if (normalized.length != VipExtensionCodeCodec.CODE_LENGTH) {
                RedeemResult.InvalidFormat
            } else {
                RedeemResult.WrongNodeOrTampered
            }
        }
        if (VipExtensionUsedCodes.isUsed(context, decoded.normalized)) {
            return RedeemResult.AlreadyUsed
        }
        val appliedAtMs = System.currentTimeMillis()
        val newDeadline = VipAccessPreferences.extendByDays(context, decoded.days)
        VipExtensionUsedCodes.markUsed(context, decoded.normalized)
        // Ledger на случай, если mesh позже сообщит «этот код уже был использован» — тогда
        // [MeshIncomingChatRepository.ingestAuraVipUsedCodes] откатит добавленные секунды.
        VipExtensionUsedCodes.recordRedeem(context, decoded.normalized, decoded.days, appliedAtMs)
        // Транслируем хэш использованного кода в mesh, чтобы соседи могли вернуть его нам
        // при следующей переустановке — блокировка «повторно не активировать» переживёт wipe.
        VipUsedCodeMeshAnnouncer.announce(context, decoded.normalized)
        return RedeemResult.Success(daysApplied = decoded.days, newDeadlineMs = newDeadline)
    }
}
