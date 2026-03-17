package com.paysetu.app.transport.model

data class TransportPayload(
    val sessionId: ByteArray,
    val sequenceNumber: Int,
    val payloadType: PayloadType,
    val payload: ByteArray
)
