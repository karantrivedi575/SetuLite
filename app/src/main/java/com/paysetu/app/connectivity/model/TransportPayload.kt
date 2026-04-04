package com.paysetu.app.connectivity.model

data class TransportPayload(
    val sessionId: ByteArray,
    val sequenceNumber: Int,
    val payloadType: PayloadType,
    val payload: ByteArray
)