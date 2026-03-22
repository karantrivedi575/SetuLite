package com.paysetu.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.paysetu.app.data.PaySetuDatabase
import com.paysetu.app.data.device.DeviceStateRepository
import com.paysetu.app.data.ledger.ChainVerifier
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.ledger.TransactionProcessor
import com.paysetu.app.data.p2p.P2PTransferManager
import com.paysetu.app.domain.policy.HeartbeatPolicy
import com.paysetu.app.domain.usecase.PerformGlobalSyncUseCase
import com.paysetu.app.domain.usecase.ReconcileLedgerUseCase
import com.paysetu.app.security.keys.KeyManager
import com.paysetu.app.security.signing.KeystoreTransactionSigner
import com.paysetu.app.domain.model.sync.SyncResponse

class PaySetuApp : Application() {

    lateinit var keyManager: KeyManager
        private set

    private val chainVerifier by lazy { ChainVerifier() }

    val database by lazy {
        Room.databaseBuilder(this, PaySetuDatabase::class.java, "paysetu-db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val ledgerRepository by lazy { LedgerRepository(database.ledgerDao(), chainVerifier) }
    val deviceRepository by lazy { DeviceStateRepository(database.deviceStateDao()) }
    val p2pManager by lazy { P2PTransferManager(this) }

    val transactionProcessor by lazy {
        TransactionProcessor(database.ledgerDao(), chainVerifier)
    }

    private val signer by lazy { KeystoreTransactionSigner() }
    private val heartbeatPolicy by lazy { HeartbeatPolicy(deviceRepository) }

    // 💡 PHASE 15 FIX: Shielding the app from Mock Sync failures
    private val performGlobalSyncUseCase by lazy {
        PerformGlobalSyncUseCase(
            ledgerRepository = ledgerRepository,
            deviceStateRepository = deviceRepository,
            reconcileLedgerUseCase = ReconcileLedgerUseCase(ledgerRepository, deviceRepository, heartbeatPolicy),
            backendApiService = object : com.paysetu.app.data.api.BackendSyncService {
                override suspend fun syncLedger(request: com.paysetu.app.domain.model.sync.SyncRequest): SyncResponse {
                    Log.d("PaySetu_Sync", "Sync attempt started (Simulated Offline)...")

                    // Simulate a slight delay for realism
                    kotlinx.coroutines.delay(2000)

                    // 💡 FIXED: Returning a valid SyncResponse with correct parameter names
                    Log.w("PaySetu_Sync", "Network unavailable. Returning mock offline response.")
                    return SyncResponse(
                        acceptedTxHashes = emptyList(),
                        rejectedTxHashes = emptyList(),
                        conflictedTxHashes = emptyList(),
                        updatedTrustScore = 1.0f,
                        serverTimestamp = System.currentTimeMillis()
                    )
                }
            }
        )
    }

    val viewModelFactory: ViewModelProvider.Factory by lazy {
        MultiViewModelFactory(
            ledgerRepository = ledgerRepository,
            transactionSigner = signer,
            deviceStateRepository = deviceRepository,
            performGlobalSyncUseCase = performGlobalSyncUseCase,
            p2pManager = p2pManager,
            transactionProcessor = transactionProcessor
        )
    }

    override fun onCreate() {
        super.onCreate()

        keyManager = KeyManager(this)
        keyManager.initialize()

        val pubKey = keyManager.getDevicePublicKey()
        Log.d("PAYSETU_INIT", "Device Identity Initialized. Public key size: ${pubKey.size} bytes")
    }
}