package com.paysetu.app.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.paysetu.app.data.PaySetuDatabase
import com.paysetu.app.data.device.DeviceStateRepository
import com.paysetu.app.data.ledger.ChainVerifier
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.domain.usecase.PerformGlobalSyncUseCase
import com.paysetu.app.security.signing.KeystoreTransactionSigner
import com.paysetu.app.ui.main.DashboardViewModel
import com.paysetu.app.ui.main.MainScreen
import com.paysetu.app.ui.payment.PaymentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var isReady by mutableStateOf(false)
    private var paymentVM by mutableStateOf<PaymentViewModel?>(null)
    private var dashboardVM by mutableStateOf<DashboardViewModel?>(null)
    private var repo by mutableStateOf<LedgerRepository?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val pVM = paymentVM
                    val dVM = dashboardVM
                    val r = repo

                    if (isReady && pVM != null && dVM != null && r != null) {
                        MainScreen(
                            paymentViewModel = pVM,
                            dashboardViewModel = dVM,
                            ledgerRepository = r
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = Room.databaseBuilder(
                    applicationContext,
                    PaySetuDatabase::class.java,
                    "paysetu-db"
                ).fallbackToDestructiveMigration().build()

                val ledgerRepo = LedgerRepository(database.ledgerDao(), ChainVerifier())
                val deviceRepo = DeviceStateRepository(database.deviceStateDao())
                val signer = KeystoreTransactionSigner()

                // 1. Create the policy instance (FIXED: Added deviceRepo)
                val policy = com.paysetu.app.domain.policy.HeartbeatPolicy(
                    deviceStateRepository = deviceRepo
                )

                // 2. Pass it into the Reconcile Use Case
                val reconcileUseCase = com.paysetu.app.domain.usecase.ReconcileLedgerUseCase(
                    ledgerRepository = ledgerRepo,
                    deviceStateRepository = deviceRepo,
                    heartbeatPolicy = policy
                )

                // 3. Create a Mock API Service
                // 3. Create a Safe Mock API Service
                val mockApiService = object : com.paysetu.app.data.api.BackendSyncService {
                    override suspend fun syncLedger(request: com.paysetu.app.domain.model.sync.SyncRequest): com.paysetu.app.domain.model.sync.SyncResponse {
                        // Simulate a 2-second network delay to test the UI spinner
                        kotlinx.coroutines.delay(2000)

                        // Throw a standard Exception so the UseCase can catch it safely
                        throw Exception("Mock network timeout")
                    }
                }

                // 4. Initialize Global Sync
                val syncUseCase = PerformGlobalSyncUseCase(
                    ledgerRepository = ledgerRepo,
                    deviceStateRepository = deviceRepo,
                    reconcileLedgerUseCase = reconcileUseCase,
                    backendApiService = mockApiService
                )

                withContext(Dispatchers.Main) {
                    val factory = MultiViewModelFactory(ledgerRepo, signer, deviceRepo, syncUseCase)

                    paymentVM = ViewModelProvider(this@MainActivity, factory)[PaymentViewModel::class.java]
                    dashboardVM = ViewModelProvider(this@MainActivity, factory)[DashboardViewModel::class.java]
                    repo = ledgerRepo

                    isReady = true
                }
            } catch (e: Exception) {
                Log.e("PaySetu", "Initialization Error", e)
            }
        }
    }
}

class MultiViewModelFactory(
    private val ledgerRepository: LedgerRepository,
    private val transactionSigner: KeystoreTransactionSigner,
    private val deviceStateRepository: DeviceStateRepository,
    private val performGlobalSyncUseCase: PerformGlobalSyncUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(PaymentViewModel::class.java) ->
                PaymentViewModel(ledgerRepository, transactionSigner) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(deviceStateRepository, performGlobalSyncUseCase) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}