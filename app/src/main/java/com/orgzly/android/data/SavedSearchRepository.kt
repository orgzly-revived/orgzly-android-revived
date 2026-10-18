package com.orgzly.android.data

import androidx.lifecycle.LiveData
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.SavedSearch
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SavedSearchRepository @Inject constructor(private val db: OrgzlyDatabase) {
    private val savedSearchDao = db.savedSearch()

    fun get(id: Long): SavedSearch? = savedSearchDao.get(id)

    fun getAll(): List<SavedSearch> = savedSearchDao.getAll()

    fun getLiveData(): LiveData<List<SavedSearch>> = savedSearchDao.getLiveData()

    fun getFlow(): Flow<List<SavedSearch>> = savedSearchDao.getFlow()

    fun getByNameIgnoreCase(name: String): List<SavedSearch> =
        savedSearchDao.getAllByNameIgnoreCase(name)

    fun create(savedSearch: SavedSearch): Long {
        val nextPosition = savedSearchDao.getNextAvailablePosition()
        return savedSearchDao.insert(savedSearch.copy(position = nextPosition))
    }

    fun update(savedSearch: SavedSearch) {
        savedSearchDao.update(savedSearch)
    }

    fun delete(ids: Set<Long>) {
        savedSearchDao.delete(ids)
    }

    fun moveUp(id: Long) {
        savedSearchDao.get(id)?.let { savedSearch ->
            savedSearchDao.getFirstAbove(savedSearch.position)?.let {
                swapPositions(savedSearch, it)
            }
        }
    }

    fun moveDown(id: Long) {
        savedSearchDao.get(id)?.let { savedSearch ->
            savedSearchDao.getFirstBelow(savedSearch.position)?.let {
                swapPositions(savedSearch, it)
            }
        }
    }

    private fun swapPositions(savedSearch: SavedSearch, other: SavedSearch) {
        db.runInTransaction {
            savedSearchDao.update(savedSearch.copy(position = other.position))
            savedSearchDao.update(other.copy(position = savedSearch.position))
        }
    }

    fun replaceAll(savedSearches: List<SavedSearch>): Int {
        db.runInTransaction {
            savedSearchDao.deleteAll()
            savedSearchDao.insert(savedSearches)
        }
        return savedSearches.size
    }
}
