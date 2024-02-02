package com.orgzly.android.data.logs

import kotlinx.coroutines.flow.Flow

interface AppLogsRepository {
    fun log(type: String, str: String)

    fun getFlow(): Flow<List<LogEntry>>
}