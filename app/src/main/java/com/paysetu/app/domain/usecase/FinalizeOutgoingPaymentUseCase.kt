package com.paysetu.app.domain.usecase

import com.paysetu.app.domain.model.PersistenceAck
import com.paysetu.app.transport.ble.BleSession

class FinalizeOutgoingPaymentUseCase(
    private val transportSession: BleSession
) {

    suspend fun execute(ack: PersistenceAck) {
        // Ledger mutation already happened during appendTransactionAtomically
        // This use case only finalizes the transport session
        transportSession.close()
    }
}
