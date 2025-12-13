package com.orgzly.android.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.orgzly.android.db.entity.AppLog
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppLogDao : BaseDao<AppLog> {
    @Query("SELECT * FROM app_logs ORDER BY timestamp")
    abstract fun getFlow(): Flow<List<AppLog>>
}
