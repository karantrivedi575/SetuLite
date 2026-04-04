package com.paysetu.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.paysetu.app.Core.database.PaySetuDatabase
import com.paysetu.app.Core.device.DeviceStateRepository
import com.paysetu.app.ledger.ChainVerifier
import com.paysetu.app.ledger.ledger.LedgerRepository
import com.paysetu.app.ledger.ledger.TransactionProcessor
import com.paysetu.app.connectivity.P2PTransferManager
import com.paysetu.app.connectivity.HeartbeatPolicy
import com.paysetu.app.sync.PerformGlobalSyncUseCase
import com.paysetu.app.ledger.ReconcileLedgerUseCase
import com.paysetu.app.security.KeyManager
import com.paysetu.app.security.KeystoreTransactionSigner
import com.paysetu.app.sync.SyncResponse
import com.paysetu.app.sync.BackendSyncService
import com.paysetu.app.sync.SyncRequest
import kotlinx.coroutines.delay

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

    // 💡 THE CRITICAL COMPONENT for Background SMS Processing
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
            backendApiService = object : BackendSyncService {
                override suspend fun syncLedger(request: SyncRequest): SyncResponse {
                    Log.d("PaySetu_Sync", "Sync attempt started (Simulated Offline)...")

                    // Simulate a slight delay for realism
                    delay(2000)

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
            application = this,
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