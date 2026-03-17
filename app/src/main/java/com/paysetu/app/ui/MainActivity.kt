package com.paysetu.app.ui

import PaymentViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import com.paysetu.app.data.PaySetuDatabase
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.ui.main.MainScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Create Room database
        val database = Room.databaseBuilder(
            applicationContext,
            PaySetuDatabase::class.java,
            "paysetu-db"
        ).build()

        // 2️⃣ Get DAO
        val ledgerDao = database.ledgerDao()

        // 3️⃣ Create repository
        val ledgerRepository = LedgerRepository(ledgerDao)

        // 4️⃣ Create ViewModel
        val paymentViewModel = PaymentViewModel(ledgerRepository)

        // 5️⃣ Set UI
        setContent {
            MainScreen(
                viewModel = paymentViewModel,
                ledgerDao = ledgerDao
            )
        }
    }
}
