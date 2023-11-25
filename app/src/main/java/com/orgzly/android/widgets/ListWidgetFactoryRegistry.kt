package com.orgzly.android.widgets

import android.widget.RemoteViewsService.RemoteViewsFactory
import com.orgzly.BuildConfig
import com.orgzly.android.util.LogUtils

object ListWidgetFactoryRegistry {

    private val factories = mutableMapOf<Long, RemoteViewsFactory>()

    @JvmStatic
    fun registerFactory(savedSearchId: Long, factory: RemoteViewsFactory) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedSearchId, factory)
        factories[savedSearchId] = factory
    }

    @JvmStatic
    fun getFactory(savedSearchId: Long): RemoteViewsFactory? {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedSearchId)
        return factories[savedSearchId]
    }

    @JvmStatic
    fun unregisterFactory(savedSearchId: Long) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedSearchId)
        factories.remove(savedSearchId)
    }


    private val TAG = ListWidgetFactoryRegistry::class.java.name

}