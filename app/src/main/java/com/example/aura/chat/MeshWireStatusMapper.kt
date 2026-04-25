package com.example.aura.chat

import com.example.aura.meshwire.MeshWireRoutingPayloadParser

/**
 * Человекочитаемые статусы для кодов [Routing.Error] (mesh.proto).
 */
object MeshWireStatusMapper {

    enum class MeshRoutingUiResult {
        Success,
        NoRoute,
        GotNak,
        Timeout,
        NoInterface,
        MaxRetransmit,
        NoChannel,
        TooLarge,
        NoResponse,
        DutyCycleLimit,
        BadRequest,
        NotAuthorized,
        PkiFailed,
        PkiUnknownPubkey,
        AdminBadSessionKey,
        AdminPublicKeyUnauthorized,
        RateLimitExceeded,
        PkiSendFailPublicKey,
        UnknownCode,
    }

    fun mapRoutingError(code: Int): MeshRoutingUiResult = when (code) {
        0 -> MeshRoutingUiResult.Success
        1 -> MeshRoutingUiResult.NoRoute
        2 -> MeshRoutingUiResult.GotNak
        3 -> MeshRoutingUiResult.Timeout
        4 -> MeshRoutingUiResult.NoInterface
        5 -> MeshRoutingUiResult.MaxRetransmit
        6 -> MeshRoutingUiResult.NoChannel
        7 -> MeshRoutingUiResult.TooLarge
        8 -> MeshRoutingUiResult.NoResponse
        9 -> MeshRoutingUiResult.DutyCycleLimit
        32 -> MeshRoutingUiResult.BadRequest
        33 -> MeshRoutingUiResult.NotAuthorized
        34 -> MeshRoutingUiResult.PkiFailed
        35 -> MeshRoutingUiResult.PkiUnknownPubkey
        36 -> MeshRoutingUiResult.AdminBadSessionKey
        37 -> MeshRoutingUiResult.AdminPublicKeyUnauthorized
        38 -> MeshRoutingUiResult.RateLimitExceeded
        39 -> MeshRoutingUiResult.PkiSendFailPublicKey
        else -> MeshRoutingUiResult.UnknownCode
    }

    fun labelRussian(result: MeshRoutingUiResult): String = when (result) {
        MeshRoutingUiResult.Success -> "Успех (маршрутизация)"
        MeshRoutingUiResult.NoRoute -> "Нет маршрута к узлу"
        MeshRoutingUiResult.GotNak -> "NAK при ретрансляции"
        MeshRoutingUiResult.Timeout -> "Таймаут доставки"
        MeshRoutingUiResult.NoInterface -> "Нет подходящего интерфейса"
        MeshRoutingUiResult.MaxRetransmit -> "Лимит ретрансляций"
        MeshRoutingUiResult.NoChannel -> "Канал недоступен"
        MeshRoutingUiResult.TooLarge -> "Пакет слишком большой"
        MeshRoutingUiResult.NoResponse -> "Нет ответа сервиса"
        MeshRoutingUiResult.DutyCycleLimit -> "Лимит duty cycle"
        MeshRoutingUiResult.BadRequest -> "Некорректный запрос"
        MeshRoutingUiResult.NotAuthorized -> "Не авторизовано"
        MeshRoutingUiResult.PkiFailed -> "Ошибка PKI"
        MeshRoutingUiResult.PkiUnknownPubkey -> "Неизвестный публичный ключ"
        MeshRoutingUiResult.AdminBadSessionKey -> "Неверный ключ сессии admin"
        MeshRoutingUiResult.AdminPublicKeyUnauthorized -> "Ключ admin не в списке"
        MeshRoutingUiResult.RateLimitExceeded -> "Превышен rate limit"
        MeshRoutingUiResult.PkiSendFailPublicKey -> "PKI: нет публичного ключа для отправки"
        MeshRoutingUiResult.UnknownCode -> "Неизвестный код маршрутизации"
    }

    /** Удобно из сырых байт Routing. */
    fun labelFromRoutingPayload(payload: ByteArray): String? {
        val code = MeshWireRoutingPayloadParser.parseErrorReason(payload) ?: return null
        return labelRussian(mapRoutingError(code))
    }
}
