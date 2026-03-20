package com.paysetu.app.data.ledger.entity

/**
 * Represents the lifecycle of an offline transaction.
 * * PENDING: Stored locally, waiting for sync.
 * ACCEPTED: Verified by the backend arbiter.
 * REJECTED: Invalid signature or format.
 * CONFLICTED: Double-spend detected by backend; balance is voided.
 */
enum class TransactionStatus {
    ACCEPTED,
    REJECTED,
    PENDING,
    CONFLICTED // ✅ Added for Phase 10 double-spend resolution
}