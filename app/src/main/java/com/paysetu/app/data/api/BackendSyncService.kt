package com.paysetu.app.data.api

import com.paysetu.app.domain.model.sync.SyncRequest
import com.paysetu.app.domain.model.sync.SyncResponse

interface BackendSyncService {
    /**
     * Authoritative endpoint that verifies offline chains and resolves double-spends.
     */
    suspend fun syncLedger(request: SyncRequest): SyncResponse
}