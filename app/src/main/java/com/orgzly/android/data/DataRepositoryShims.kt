package com.orgzly.android.data

import androidx.annotation.VisibleForTesting
import com.orgzly.android.db.OrgzlyDatabase
import javax.inject.Inject

/** Repositories used by the legacy [DataRepository] facade during incremental extraction. */
class DataRepositoryShims @Inject constructor(
    val savedSearches: SavedSearchRepository
) {
    companion object {
        /** Creates the facade's delegates using the test database. */
        @JvmStatic
        @VisibleForTesting
        fun forTest(db: OrgzlyDatabase): DataRepositoryShims =
            DataRepositoryShims(SavedSearchRepository(db))
    }
}
