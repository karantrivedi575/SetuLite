// File: PaySetuApp.kt
package com.paysetu.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.paysetu.app.Core.database.PaySetuDatabase
import com.paysetu.app.ledger.ChainVerifier
import com.paysetu.app.ledger.ledger.LedgerRepository
import com.paysetu.app.ledger.ledger.TransactionProcessor
import com.paysetu.app.connectivity.P2PTransferManager
import com.paysetu.app.connectivity.HeartbeatPolicy
import com.paysetu.app.sync.PerformGlobalSyncUseCase
import com.paysetu.app.ledger.ReconcileLedgerUseCase

import com.paysetu.app.security.KeyManager
import com.paysetu.app.security.TransactionSigner
import com.paysetu.app.sync.SyncResponse
import com.paysetu.app.sync.BackendSyncService
import com.paysetu.app.sync.SyncRequest
import kotlinx.coroutines.delay

// 💡 IMPORTS FOR THE ENGINE AND SECURITY MODULES
import com.paysetu.app.payment.OfflinePaymentEngine
import com.paysetu.app.security.DeviceIntegrityChecker
import com.paysetu.app.security.DeviceIntegrityCheckerImpl
import com.paysetu.app.security.GenesisHashProvider
import com.paysetu.app.security.PinAuthorizer

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
    val deviceRepository by lazy { `DeviceState.kt`(database.deviceStateDao()) }
    val p2pManager by lazy { P2PTransferManager(this) }

    // 💡 THE CRITICAL COMPONENT for Background SMS Processing
    val transactionProcessor by lazy {
        TransactionProcessor(database.ledgerDao(), chainVerifier)
    }

    private val signer: TransactionSigner by lazy { KeyManager(this) }
    private val heartbeatPolicy by lazy { HeartbeatPolicy(deviceRepository) }

    // ==========================================
    // 💡 FIXED: SECURITY MODULE INITIALIZERS
    // ==========================================

    // 1. Pass the keyManager as the KeyProvider
    private val genesisHashProvider by lazy { GenesisHashProvider(keyManager) }

    // 2. Use the concrete 'Impl' class and pass 'this' as the Context
    private val deviceIntegrityChecker: DeviceIntegrityChecker by lazy {
        DeviceIntegrityCheckerImpl(this)
    }

    // 3. Inline implementation for PinAuthorizer to satisfy the interface requirement
    private val pinAuthorizer: PinAuthorizer by lazy {
        object : PinAuthorizer {
            override fun authorize(pin: String): Boolean {
                // TODO: Replace with your actual secure PIN validation logic later
                Log.d("PaySetu_Security", "Validating PIN...")
                return pin == "1234" // Default dummy PIN for testing
            }
        }
    }

    // 💡 THE UNIFIED ENGINE
    val offlinePaymentEngine by lazy {
        OfflinePaymentEngine(
            genesisHashProvider = genesisHashProvider,
            pinAuthorizer = pinAuthorizer,
            deviceIntegrityChecker = deviceIntegrityChecker,
            heartbeatPolicy = heartbeatPolicy,
            signer = signer,
            ledgerRepository = ledgerRepository,
            keyProvider = keyManager
        )
    }

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
            transactionProcessor = transactionProcessor,
            offlinePaymentEngine = offlinePaymentEngine
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