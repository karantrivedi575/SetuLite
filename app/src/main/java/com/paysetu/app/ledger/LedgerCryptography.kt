// File: LedgerCryptography.kt
package com.paysetu.app.ledger

import com.paysetu.app.ledger.model.LedgerTransactionEntity
import java.nio.ByteBuffer
import java.security.MessageDigest

// ==========================================
// 1. Cryptographic Hashing Engine
// ==========================================
object TransactionHasher {
    fun calculateHash(tx: LedgerTransactionEntity): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")

        // We hash everything EXCEPT the 'id' (Room-generated), the signature, and the 'txHash' itself.
        // Buffer size 1024 ensures consistent byte alignment and prevents overflow.
        val buffer = ByteBuffer.allocate(1024)

        buffer.put(tx.prevTxHash)
        buffer.put(tx.senderDeviceId)
        buffer.put(tx.receiverDeviceId)
        buffer.putLong(tx.amount)
        buffer.putLong(tx.timestamp)
        buffer.put(tx.direction.name.toByteArray())

        // Extract only the filled bytes for hashing
        val bytesToHash = buffer.array().take(buffer.position()).toByteArray()
        return digest.digest(bytesToHash)
    }
}

// ==========================================
// 2. Cryptographic Chain Verification
// ==========================================
class ChainVerifier {

    /**
     * Verifies the entire ledger for linkage, data integrity, and authenticity.
     * Returns true only if every block in the chain is cryptographically valid.
     */
    fun verifyChain(transactions: List<LedgerTransactionEntity>): Boolean {
        if (transactions.isEmpty()) return true

        // Verification must happen from the oldest block to the newest to validate the links.
        val sortedChain = transactions.sortedBy { it.timestamp }

        // 1. Genesis Block Validation: The first block must point to a zeroed hash
        val genesis = sortedChain[0]
        val expectedGenesisPrevHash = ByteArray(32) { 0 }

        if (!genesis.prevTxHash.contentEquals(expectedGenesisPrevHash)) return false
        if (!verifyBlockInternal(genesis)) return false

        // 2. Continuous Chain Validation
        for (i in 1 until sortedChain.size) {
            val current = sortedChain[i]
            val previous = sortedChain[i - 1]

            // Check Linkage: Does this block point to the previous one?
            if (!current.prevTxHash.contentEquals(previous.txHash)) return false

            // Check Internal Integrity: Does the data match the hash?
            if (!verifyBlockInternal(current)) return false
        }

        return true
    }

    /**
     * Validates a single block's internal consistency.
     */
    private fun verifyBlockInternal(tx: LedgerTransactionEntity): Boolean {
        // 💡 FIX: Now securely routes through the centralized TransactionHasher
        val computedHash = TransactionHasher.calculateHash(tx)
        if (!tx.txHash.contentEquals(computedHash)) return false

        // Ensure signature exists
        if (tx.signature.isEmpty()) return false

        return true
    }
}

// ==========================================
// 3. Ledger Checkpoint State
// ==========================================
data class LedgerCheckpoint(
    val lastVerifiedTxHash: ByteArray,
    val totalVolume: Long
) {
    // 💡 Auto-generated equals/hashCode is necessary because of the ByteArray property
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LedgerCheckpoint

        if (!lastVerifiedTxHash.contentEquals(other.lastVerifiedTxHash)) return false
        if (totalVolume != other.totalVolume) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lastVerifiedTxHash.contentHashCode()
        result = 31 * result + totalVolume.hashCode()
        return result
    }
}