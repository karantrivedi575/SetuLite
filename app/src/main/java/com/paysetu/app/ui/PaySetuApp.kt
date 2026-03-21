package com.paysetu.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.paysetu.app.data.PaySetuDatabase
import com.paysetu.app.data.device.DeviceStateRepository
import com.paysetu.app.data.ledger.ChainVerifier
import com.paysetu.app.data.ledger.LedgerRepository
import com.paysetu.app.data.p2p.P2PTransferManager
import com.paysetu.app.domain.policy.HeartbeatPolicy
import com.paysetu.app.domain.usecase.PerformGlobalSyncUseCase
import com.paysetu.app.domain.usecase.ReconcileLedgerUseCase
import com.paysetu.app.security.keys.KeyManager
import com.paysetu.app.security.signing.KeystoreTransactionSigner

class PaySetuApp : Application() {

    // 1. Core Security (Phase 6 & 10)
    lateinit var keyManager: KeyManager
        private set

    // 2. Database & Repositories (Phase 11)
    val database by lazy {
        Room.databaseBuilder(this, PaySetuDatabase::class.java, "paysetu-db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val ledgerRepository by lazy { LedgerRepository(database.ledgerDao(), ChainVerifier()) }
    val deviceRepository by lazy { DeviceStateRepository(database.deviceStateDao()) }

    // 3. Phase 12: Offline P2P Bridge
    val p2pManager by lazy { P2PTransferManager(this) }

    // 4. Use Cases & Factory
    private val signer by lazy { KeystoreTransactionSigner() }
    private val heartbeatPolicy by lazy { HeartbeatPolicy(deviceRepository) }

    private val performGlobalSyncUseCase by lazy {
        PerformGlobalSyncUseCase(
            ledgerRepository = ledgerRepository,
            deviceStateRepository = deviceRepository,
            reconcileLedgerUseCase = ReconcileLedgerUseCase(ledgerRepository, deviceRepository, heartbeatPolicy),
            backendApiService = object : com.paysetu.app.data.api.BackendSyncService {
                override suspend fun syncLedger(request: com.paysetu.app.domain.model.sync.SyncRequest): com.paysetu.app.domain.model.sync.SyncResponse {
                    kotlinx.coroutines.delay(2000)
                    throw Exception("Mock network timeout")
                }
            }
        )
    }

    val viewModelFactory: ViewModelProvider.Factory by lazy {
        MultiViewModelFactory(
            ledgerRepository,
            signer,
            deviceRepository,
            performGlobalSyncUseCase,
            p2pManager // <--- ADD THIS LINE
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize hardware keys
        keyManager = KeyManager(this)
        keyManager.initialize()

        val pubKey = keyManager.getDevicePublicKey()
        Log.d("PAYSETU_INIT", "Device Identity Initialized. Public key size: ${pubKey.size} bytes")
    }
}