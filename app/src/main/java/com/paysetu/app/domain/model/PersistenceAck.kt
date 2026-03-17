package com.paysetu.app.domain.model

data class PersistenceAck(
    val txHash: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersistenceAck

        if (!txHash.contentEquals(other.txHash)) return false

        return true
    }

    override fun hashCode(): Int {
        return txHash.contentHashCode()
    }
}
