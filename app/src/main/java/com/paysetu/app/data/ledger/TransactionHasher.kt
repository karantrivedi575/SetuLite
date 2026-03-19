package com.paysetu.app.data.ledger

import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import java.nio.ByteBuffer
import java.security.MessageDigest

object TransactionHasher {
    fun calculateHash(tx: LedgerTransactionEntity): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")

        // We hash everything EXCEPT the 'id' (Room-generated) and the 'txHash' itself
        val buffer = ByteBuffer.allocate(256) // Sufficient space for fields

        buffer.put(tx.prevTxHash)
        buffer.put(tx.senderDeviceId)
        buffer.put(tx.receiverDeviceId)
        buffer.putLong(tx.amount)
        buffer.putLong(tx.timestamp)
        buffer.put(tx.direction.name.toByteArray())

        return digest.digest(buffer.array().take(buffer.position()).toByteArray())
    }
}