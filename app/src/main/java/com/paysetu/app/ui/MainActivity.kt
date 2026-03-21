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
import com.paysetu.app.domain.usecase.PerformGlobalSyncUseCase
import com.paysetu.app.security.signing.KeystoreTransactionSigner
import com.paysetu.app.ui.common.PermissionGate
import com.paysetu.app.ui.main.DashboardViewModel
import com.paysetu.app.ui.main.MainScreen
import com.paysetu.app.ui.payment.PaymentViewModel

class MainActivity : ComponentActivity() {

    // 1. Use the standard Android delegate to get ViewModels from your App Container.
    // This automatically saves your ViewModels when the screen rotates!
    private val paymentVM: PaymentViewModel by viewModels {
        (application as PaySetuApp).viewModelFactory
    }

    private val dashboardVM: DashboardViewModel by viewModels {
        (application as PaySetuApp).viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Grab the repository directly from the App class
        val repo = (application as PaySetuApp).ledgerRepository

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // 3. Wrap your MainScreen in the PermissionGate
                    // The UI will only load once the user grants Bluetooth/Location access.
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

// Keeping your factory here so it knows how to build the ViewModels
class MultiViewModelFactory(
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: KeystoreTransactionSigner,
    private val deviceStateRepository: DeviceStateRepository,
    private val performGlobalSyncUseCase: PerformGlobalSyncUseCase,
    private val p2pManager: com.paysetu.app.data.p2p.P2PTransferManager // <--- ADD THIS PARAMETER
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(PaymentViewModel::class.java) ->
                // Pass it into the PaymentViewModel
                PaymentViewModel(ledgerRepository, transactionSigner, p2pManager) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(deviceStateRepository, performGlobalSyncUseCase) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}