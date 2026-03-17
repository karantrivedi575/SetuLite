package com.paysetu.app.domain.usecase


import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.data.ledger.entity.TransactionStatus
import com.paysetu.app.domain.model.SignedTransaction
import com.paysetu.app.domain.policy.HeartbeatPolicy
import com.paysetu.app.domain.security.DeviceIntegrityChecker
import com.paysetu.app.domain.security.PinAuthorizer
import com.paysetu.app.domain.security.TransactionSigner
import com.paysetu.app.security.genesis.GenesisHashProvider
import java.security.MessageDigest

class SendPaymentUseCase(

    private val genesisHashProvider: GenesisHashProvider,
    private val pinAuthorizer: PinAuthorizer,
    private val deviceIntegrityChecker: DeviceIntegrityChecker,
    private val heartbeatPolicy: HeartbeatPolicy,
    private val signer: TransactionSigner,
    private val ledgerRepository: LedgerRepository
) {

    suspend fun execute(amount: Long): SignedTransaction{


    // Phase 5: device trust
        require(deviceIntegrityChecker.isDeviceTrusted())

        // Phase 5: heartbeat
        heartbeatPolicy.enforce()

        // Phase 5: PIN
        require(pinAuthorizer.authorize()) {
            "User authentication required"
        }


        // Phase 6: payload creation
        val payload = amount.toString().toByteArray()

        val txHash = MessageDigest
            .getInstance("SHA-256")
            .digest(payload)

        val signature = signer.sign(txHash)

        val signedTx = SignedTransaction(
            txHash = txHash,
            prevTxHash = genesisHashProvider.getGenesisHash(),

            payload = payload,
            signature = signature
        )

        // Phase 7: persist atomically
        val entity = LedgerTransactionEntity(
            txHash = signedTx.txHash,
            prevTxHash = signedTx.prevTxHash,
            senderDeviceId = ByteArray(0),
            receiverDeviceId = ByteArray(0),
            amount = amount,
            timestamp = System.currentTimeMillis(),
            signature = signedTx.signature,
            direction = TransactionDirection.OUTGOING,
            status = TransactionStatus.PENDING
        )

        ledgerRepository.appendTransactionAtomically(entity)

        // Phase 9: return data for transport layer
        return signedTx
    }
}
