package com.paysetu.app.ui

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.paysetu.app.ui.PaySetuApp
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
import com.paysetu.app.ui.theme.PaySetuTheme

class MainActivity : FragmentActivity() {

    private val paymentVM: PaymentViewModel by viewModels {
        (application as PaySetuApp).viewModelFactory
    }

    private val dashboardVM: DashboardViewModel by viewModels {
        (application as PaySetuApp).viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 💡 FORCING THE "INFINITE OLED" LOOK
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val repo = (application as PaySetuApp).ledgerRepository

        setContent {
            PaySetuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    // 🛡️ THE FIX: PermissionGate no longer takes parameters!
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
 * 💡 MultiViewModelFactory: Injects hardware modules into the UI Layer.
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
            modelClass.isAssignableFrom(PaymentViewModel::class.java) -> {
                PaymentViewModel(
                    ledgerRepository = ledgerRepository,
                    transactionSigner = transactionSigner,
                    p2pManager = p2pManager,
                    transactionProcessor = transactionProcessor
                ) as T
            }

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