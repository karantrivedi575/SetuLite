package com.paysetu.app.transport.ble

import com.paysetu.app.transport.model.PayloadType
import com.paysetu.app.transport.model.TransportPayload

import java.nio.ByteBuffer

class BlePaymentTransport(
    private val session: BleSession,
    private val sendBytes: (ByteArray) -> Unit,
    private val onTransactionReceived: (ByteArray) -> Unit   // 🔹 changed
) {

    fun send(payloadType: PayloadType, rawPayload: ByteArray) {
        if (!session.isEstablished() && payloadType != PayloadType.HANDSHAKE) {
            session.close()
            return
        }

        val payload = TransportPayload(
            sessionId = session.sessionId,
            sequenceNumber = session.nextOutgoingSequence(),
            payloadType = payloadType,
            payload = rawPayload
        )

        sendBytes(serialize(payload))
    }

    fun receive(bytes: ByteArray) {
        val payload = deserialize(bytes)

        if (!session.validateIncoming(payload.sessionId, payload.sequenceNumber)) {
            session.close()
            return
        }

        // ✅ ONLY forward transaction payloads
        if (payload.payloadType == PayloadType.TRANSACTION) {
            onTransactionReceived(payload.payload)
        }
    }

    private fun serialize(payload: TransportPayload): ByteArray {
        val buffer = ByteBuffer.allocate(32 + 4 + 1 + payload.payload.size)

        buffer.put(payload.sessionId)
        buffer.putInt(payload.sequenceNumber)
        buffer.put(payload.payloadType.ordinal.toByte())
        buffer.put(payload.payload)

        return buffer.array()
    }

    private fun deserialize(bytes: ByteArray): TransportPayload {
        val buffer = ByteBuffer.wrap(bytes)

        val sessionId = ByteArray(32)
        buffer.get(sessionId)

        val sequence = buffer.int
        val type = PayloadType.entries[buffer.get().toInt()]
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)

        return TransportPayload(
            sessionId = sessionId,
            sequenceNumber = sequence,
            payloadType = type,
            payload = payload
        )
    }
}
