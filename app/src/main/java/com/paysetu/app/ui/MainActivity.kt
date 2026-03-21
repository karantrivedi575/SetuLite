package com.paysetu.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.paysetu.app.data.device.DeviceStateRepository
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.TransactionProcessor
import com.paysetu.app.data.p2p.P2PTransferManager
import com.paysetu.app.domain.usecase.PerformGlobalSyncUseCase
import com.paysetu.app.security.signing.KeystoreTransactionSigner
import com.paysetu.app.ui.common.PermissionGate
import com.paysetu.app.ui.main.DashboardViewModel
import com.paysetu.app.ui.main.MainScreen
import com.paysetu.app.ui.payment.PaymentViewModel

class MainActivity : ComponentActivity() {

    // 1. ViewModels injected via the custom factory in PaySetuApp
    private val paymentVM: PaymentViewModel by viewModels {
        (application as PaySetuApp).viewModelFactory
    }

    private val dashboardVM: DashboardViewModel by viewModels {
        (application as PaySetuApp).viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Grab the repository directly from the App class for the MainScreen
        val repo = (application as PaySetuApp).ledgerRepository

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 3. Wrap your MainScreen in the PermissionGate
                    // Ensures Bluetooth/Location radios are ready before P2P starts.
                    PermissionGate {
                        MainScreen(
                            paymentViewModel = paymentVM,
                            dashboardViewModel = dashboardVM,
                            ledgerRepository = repo
                        )
                    }
                }
            }
        }
    }
}

/**
 * MultiViewModelFactory handles the creation of all ViewModels in the app.
 * It acts as the bridge between the Data Layer and the UI Layer.
 */
class MultiViewModelFactory(
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: KeystoreTransactionSigner,
    private val deviceStateRepository: DeviceStateRepository,
    private val performGlobalSyncUseCase: PerformGlobalSyncUseCase,
    private val p2pManager: P2PTransferManager,
    private val transactionProcessor: TransactionProcessor
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            // Handle PaymentViewModel (Requires Ledger, Signer, P2P Manager, and Processor)
            modelClass.isAssignableFrom(PaymentViewModel::class.java) -> {
                PaymentViewModel(
                    ledgerRepository = ledgerRepository,
                    transactionSigner = transactionSigner,
                    p2pManager = p2pManager,
                    transactionProcessor = transactionProcessor
                ) as T
            }

            // Handle DashboardViewModel
            // 💡 FIXED: Now passes deviceStateRepository correctly
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(
                    deviceStateRepository = deviceStateRepository,
                    performGlobalSyncUseCase = performGlobalSyncUseCase
                ) as T
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}