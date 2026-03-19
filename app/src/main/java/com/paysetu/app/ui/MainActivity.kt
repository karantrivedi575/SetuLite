package com.paysetu.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.paysetu.app.data.PaySetuDatabase
import com.paysetu.app.data.ledger.ChainVerifier
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.security.signing.KeystoreTransactionSigner
import com.paysetu.app.ui.main.MainScreen
import com.paysetu.app.ui.payment.PaymentViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Create Room database
        val database = Room.databaseBuilder(
            applicationContext,
            PaySetuDatabase::class.java,
            "paysetu-db"
        ).build()

        // 2️⃣ Initialize Security Components
        val ledgerDao = database.ledgerDao()
        val chainVerifier = ChainVerifier()
        val transactionSigner = KeystoreTransactionSigner()

        // 3️⃣ Create repository with the Verifier
        val ledgerRepository = LedgerRepository(
            ledgerDao = ledgerDao,
            chainVerifier = chainVerifier
        )

        // 4️⃣ Create ViewModel with Repository and Signer
        val paymentViewModel = PaymentViewModel(
            ledgerRepository = ledgerRepository,
            transactionSigner = transactionSigner
        )

        // 5️⃣ Set UI
        setContent {
            // It is better practice to pass the Repository to the UI
            // instead of the raw DAO to ensure data is always verified.
            MainScreen(
                viewModel = paymentViewModel,
                ledgerRepository = ledgerRepository
            )
        }
    }
}