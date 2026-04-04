// File: TransportModels.kt
package com.paysetu.app.connectivity.model

/**
 * Defines the type of data being transmitted over the wire.
 */
enum class PayloadType {
    HANDSHAKE,
    TRANSACTION,
    ACK,
    NACK
}

/**
 * The standard wrapper for all data moving between devices.
 * Includes session tracking and sequencing to ensure reliability.
 */
data class TransportPayload(
    val sessionId: ByteArray,
    val sequenceNumber: Int,
    val payloadType: PayloadType,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransportPayload

        if (!sessionId.contentEquals(other.sessionId)) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (payloadType != other.payloadType) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.contentHashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + payloadType.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Represents a cryptographically signed transaction ready for transport.
 * This is the core data structure for PaySetu's offline value transfer.
 */
data class SignedTransaction(
    val txHash: ByteArray,
    val prevTxHash: ByteArray,
    val payload: ByteArray,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedTransaction

        if (!txHash.contentEquals(other.txHash)) return false
        if (!prevTxHash.contentEquals(other.prevTxHash)) return false
        if (!payload.contentEquals(other.payload)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = txHash.contentHashCode()
        result = 31 * result + prevTxHash.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}