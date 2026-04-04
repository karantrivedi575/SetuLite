package com.paysetu.app.payment

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
