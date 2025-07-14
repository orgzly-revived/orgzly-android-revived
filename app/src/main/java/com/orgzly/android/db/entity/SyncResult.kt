package com.orgzly.android.db.entity

/**
 * Represents the outcome/result of the last completed synchronization *action* for a Book.
 * This is distinct from BookSyncStatus which determines the *next* sync action needed.
 */
enum class SyncResult {
    /** Sync action completed successfully (changes might have been made or not). */
    SUCCESS,

    // NO_CHANGE removed, considered part of SUCCESS

    /** Sync action failed due to an error (network, credentials, file access, etc.). */
    ERROR,

    /** Sync action resulted in a conflict state (e.g., both local and remote modified). */
    CONFLICT
} 