package com.example.aura.mesh

/**
 * Исходящие сообщения: подтверждение доставки на телефон приходит как [PortNum.ROUTING_APP]
 * и обрабатывается в [com.example.aura.bluetooth.ChannelChatManager.handleRouting].
 *
 * Отдельная отправка ACK на ноду для каждого входящего DM не используется в типичном клиенте mesh:
 * чтение FromRadio достаточно для «доставки на телефон». При появлении требования протокола
 * (например, расширенный client flow) сюда можно добавить запись в ToRadio.
 */
object MeshIncomingDeliveryAck
