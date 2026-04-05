// File: MainActivity.kt
package com.paysetu.app

import android.app.Application
import android.os.Bundle
import android.util.Log // 💡 Added for the BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler // 💡 ADDED: Modern predictive back handling
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
import com.paysetu.app.ledger.ledger.LedgerRepository
import com.paysetu.app.ledger.ledger.TransactionProcessor
import com.paysetu.app.connectivity.P2PTransferManager
import com.paysetu.app.sync.PerformGlobalSyncUseCase
import com.paysetu.app.security.TransactionSigner
import com.paysetu.app.connectivity.ui.PermissionGate
import com.paysetu.app.home.DashboardViewModel
import com.paysetu.app.home.MainScreen
import com.paysetu.app.payment.PaymentViewModel
import com.paysetu.app.payment.OfflinePaymentEngine // 💡 ADDED: Import for the new engine
import com.paysetu.app.Core.theme.PaySetuTheme

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
                    // 💡 THE FIX: Android 13+ Predictive Back Handling.
                    // This intercepts the system back gesture gracefully and satisfies the Manifest warning.
                    BackHandler(enabled = true) {
                        Log.d("MainActivity", "System Back Gesture intercepted safely.")
                        // You can handle custom back stack navigation here.
                        // If you are at the root of the app, call finish() to exit gracefully.
                        finish()
                    }

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
    private val application: Application,
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: TransactionSigner,
    private val deviceStateRepository: `DeviceState.kt`,
    private val performGlobalSyncUseCase: PerformGlobalSyncUseCase,
    private val p2pManager: P2PTransferManager,
    private val transactionProcessor: TransactionProcessor,
    private val offlinePaymentEngine: OfflinePaymentEngine // 💡 ADDED: Injecting the new engine
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(PaymentViewModel::class.java) -> {
                PaymentViewModel(
                    application = application,
                    ledgerRepository = ledgerRepository,
                    transactionSigner = transactionSigner,
                    p2pManager = p2pManager,
                    transactionProcessor = transactionProcessor,
                    offlinePaymentEngine = offlinePaymentEngine // 💡 THE FIX: Safely passed to ViewModel
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