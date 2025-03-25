package com.orgzly.android.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.db.OrgzlyDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for database migrations.
 * This class tests migration between different database versions.
 */
class DatabaseMigrationTest : OrgzlyTest() {
    /**
     * Test migration from version 157 to 158 (using the exposed migration method)
     */
    @Test
    fun migrate157To158WithExposedMethod() {
        // Get a reference to the database
        val db = database
        
        // First, clear any existing searches
        clearSearches(db)
        
        // Insert search queries without the sort clause (similar to version 157)
        insertVersion157Searches(db)
        
        // Verify searches are inserted correctly
        val searchesBefore = dataRepository.getSavedSearches()
        assertEquals(4, searchesBefore.size)
        assertEquals(".it.done ad.7", searchesBefore[0].query)
        assertEquals(".it.done s.ge.today ad.3", searchesBefore[1].query)
        assertEquals("s.today .it.done", searchesBefore[2].query)
        assertEquals("i.todo", searchesBefore[3].query)
        
        // Use the exposed migration method
        val sqliteDb = (db as androidx.room.RoomDatabase).openHelper.writableDatabase
        OrgzlyDatabase.runMigration157To158(sqliteDb)
        
        // Verify that the searches have been updated with the sort clause
        verifyMigratedSearches()
    }
    
    /**
     * Clear all existing saved searches
     */
    private fun clearSearches(db: OrgzlyDatabase) {
        db.runInTransaction {
            db.savedSearch().deleteAll()
        }
    }
    
    /**
     * Insert searches as they would be in version 157 (without sort clause)
     */
    private fun insertVersion157Searches(db: OrgzlyDatabase) {
        val sqliteDb = (db as androidx.room.RoomDatabase).openHelper.writableDatabase
        
        sqliteDb.execSQL("INSERT INTO searches (`name`, `query`, `position`) VALUES (\"Agenda\", \".it.done ad.7\", 1)")
        sqliteDb.execSQL("INSERT INTO searches (`name`, `query`, `position`) VALUES (\"Next 3 days\", \".it.done s.ge.today ad.3\", 2)")
        sqliteDb.execSQL("INSERT INTO searches (`name`, `query`, `position`) VALUES (\"Scheduled\", \"s.today .it.done\", 3)")
        sqliteDb.execSQL("INSERT INTO searches (`name`, `query`, `position`) VALUES (\"To Do\", \"i.todo\", 4)")
    }
    
    /**
     * Verify that the searches have been properly migrated
     */
    private fun verifyMigratedSearches() {
        val searchesAfter = dataRepository.getSavedSearches()
        assertEquals(4, searchesAfter.size)
        
        assertEquals("Agenda", searchesAfter[0].name)
        assertEquals(".it.done ad.7 o.s", searchesAfter[0].query)
        assertEquals(1, searchesAfter[0].position)
        
        assertEquals("Next 3 days", searchesAfter[1].name)
        assertEquals(".it.done s.ge.today ad.3 o.s", searchesAfter[1].query)
        assertEquals(2, searchesAfter[1].position)
        
        assertEquals("Scheduled", searchesAfter[2].name)
        assertEquals("s.today .it.done o.s", searchesAfter[2].query)
        assertEquals(3, searchesAfter[2].position)
        
        assertEquals("To Do", searchesAfter[3].name)
        assertEquals("i.todo o.s", searchesAfter[3].query)
        assertEquals(4, searchesAfter[3].position)
    }
} 