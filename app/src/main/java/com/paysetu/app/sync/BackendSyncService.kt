package com.paysetu.app.sync

interface BackendSyncService {
    /**
     * Authoritative endpoint that verifies offline chains and resolves double-spends.
     */
    suspend fun syncLedger(request: SyncRequest): SyncResponse
}