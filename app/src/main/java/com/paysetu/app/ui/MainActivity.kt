package com.paysetu.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.paysetu.app.data.PaySetuDatabase
import com.paysetu.app.data.ledger.ChainVerifier
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.security.signing.KeystoreTransactionSigner
import com.paysetu.app.ui.main.MainScreen
import com.paysetu.app.ui.payment.PaymentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup global states
        var isReady by mutableStateOf(false)
        var paymentViewModel: PaymentViewModel? by mutableStateOf(null)
        var ledgerRepository: LedgerRepository? by mutableStateOf(null)

        // 2. Single setContent call with a conditional swap
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isReady && paymentViewModel != null) {
                        MainScreen(
                            viewModel = paymentViewModel!!,
                            ledgerRepository = ledgerRepository!!
                        )
                    } else {
                        // Immediate placeholder prevents OS from flagging ANR
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        // 3. Background initialization (IO safety)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = Room.databaseBuilder(
                    applicationContext,
                    PaySetuDatabase::class.java,
                    "paysetu-db"
                ).fallbackToDestructiveMigration().build()

                val repo = LedgerRepository(database.ledgerDao(), ChainVerifier())
                val signer = KeystoreTransactionSigner()

                withContext(Dispatchers.Main) {
                    val factory = PaymentViewModelFactory(repo, signer)
                    paymentViewModel = ViewModelProvider(this@MainActivity, factory)[PaymentViewModel::class.java]
                    ledgerRepository = repo
                    isReady = true // Triggers smooth UI swap
                }
            } catch (e: Exception) {
                android.util.Log.e("PaySetu", "Initialization Error", e)
            }
        }
    }
}

class PaymentViewModelFactory(
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: KeystoreTransactionSigner
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return PaymentViewModel(ledgerRepository, transactionSigner) as T
    }
}