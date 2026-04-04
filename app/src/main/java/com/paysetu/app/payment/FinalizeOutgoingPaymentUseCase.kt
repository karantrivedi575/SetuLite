package com.paysetu.app.payment

import com.paysetu.app.ledger.PersistenceAck
import com.paysetu.app.connectivity.BleSession

class FinalizeOutgoingPaymentUseCase(
    private val transportSession: BleSession
) {

    suspend fun execute(ack: PersistenceAck) {
        // Ledger mutation already happened during appendTransactionAtomically
        // This use case only finalizes the transport session
        transportSession.close()
    }
}
