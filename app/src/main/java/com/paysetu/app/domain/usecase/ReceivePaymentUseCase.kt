package com.paysetu.app.domain.usecase

import com.paysetu.app.domain.model.PersistenceAck
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.entity.LedgerTransactionEntity
import com.paysetu.app.data.ledger.entity.TransactionDirection
import com.paysetu.app.data.ledger.entity.TransactionStatus
import com.paysetu.app.security.genesis.GenesisHashProvider
import com.paysetu.app.domain.security.DeviceIntegrityChecker

class ReceivePaymentUseCase(
    private val genesisHashProvider: GenesisHashProvider,
    private val deviceIntegrityChecker: DeviceIntegrityChecker,
    private val ledgerRepository: LedgerRepository
) {

    suspend fun execute(receivedTx: LedgerTransactionEntity): PersistenceAck {

        // Phase 5: Device trust
        require(deviceIntegrityChecker.isDeviceTrusted()) {
            "Device integrity check failed"
        }

        // Phase 6: Genesis validation
        require(
            receivedTx.prevTxHash.contentEquals(
                genesisHashProvider.getGenesisHash()
            )
        ) {
            "Invalid genesis hash"
        }

        // Phase 7: Normalize + append
        val acceptedTx = receivedTx.copy(
            direction = TransactionDirection.INCOMING,
            status = TransactionStatus.ACCEPTED
        )

        ledgerRepository.appendTransactionAtomically(acceptedTx)

        // Phase 9: ACK returned to transport
        return PersistenceAck(acceptedTx.txHash)
    }
}
