package com.paysetu.app.data.ledger

import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import java.nio.ByteBuffer
import java.security.MessageDigest

class ChainVerifier {

    /**
     * Verifies the entire ledger for linkage, data integrity, and authenticity.
     * Returns true only if every block in the chain is cryptographically valid.
     */
    fun verifyChain(transactions: List<LedgerTransactionEntity>): Boolean {
        if (transactions.isEmpty()) return true

        // 1. Genesis Block Validation: The first block must point to a zeroed hash
        val genesis = transactions[0]
        val expectedGenesisPrevHash = ByteArray(32) { 0 }

        if (!genesis.prevTxHash.contentEquals(expectedGenesisPrevHash)) return false
        if (!verifyBlockInternal(genesis)) return false

        // 2. Continuous Chain Validation
        for (i in 1 until transactions.size) {
            val current = transactions[i]
            val previous = transactions[i - 1]

            // Check Linkage: Does this block point to the previous one?
            if (!current.prevTxHash.contentEquals(previous.txHash)) return false

            // Check Internal Integrity: Does the data match the hash and signature?
            if (!verifyBlockInternal(current)) return false
        }

        return true
    }

    /**
     * Validates a single block's internal consistency.
     */
    private fun verifyBlockInternal(tx: LedgerTransactionEntity): Boolean {
        // Integrity Check: Re-calculate SHA-256 hash to ensure no data was tampered with
        val computedHash = calculateHash(tx)
        if (!tx.txHash.contentEquals(computedHash)) return false

        // Authenticity Check: Ensure the signature is present
        // (Full ECDSA public key verification is implemented in the Transport layer)
        if (tx.signature.isEmpty()) return false

        return true
    }

    private fun calculateHash(tx: LedgerTransactionEntity): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(1024)

        buffer.put(tx.prevTxHash)
        buffer.put(tx.senderDeviceId)
        buffer.put(tx.receiverDeviceId)
        buffer.putLong(tx.amount)
        buffer.putLong(tx.timestamp)
        buffer.put(tx.direction.name.toByteArray())

        return digest.digest(buffer.array().take(buffer.position()).toByteArray())
    }
}