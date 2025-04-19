package com.orgzly.android.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orgzly.android.OrgzlyTest // Make sure this is the correct base class if needed
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.SyncResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest : OrgzlyTest() { // Extend OrgzlyTest as per plan

    private val TEST_DB = "migration-test"
    private val DB_NAME = OrgzlyDatabase.NAME // Use the correct constant name from OrgzlyDatabase

    // Rule for MigrationTestHelper - Add this back
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrgzlyDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    // Removed @Before and @After setup/teardown that might duplicate OrgzlyTest

    /**
     * Test migration from version 157 to 158 adds sync action result columns.
     */
    @Test
    @Throws(IOException::class) // Add Throws annotation for helper methods
    fun migrate157To158_addsSyncActionColumns() {
        // 1. Create V157 database using MigrationTestHelper
        var db = helper.createDatabase(TEST_DB, 157).apply {
            // 2. Setup: Insert v157-like data, including the NOT NULL column
            execSQL("INSERT INTO books (id, name, is_dummy, is_modified) VALUES (1, 'Test Book For Migration', 0, 0)")
            close()
        }

        // 3. Run migration to V158 using MigrationTestHelper
        // Pass the static migration from OrgzlyDatabase
        db = helper.runMigrationsAndValidate(TEST_DB, 158, true, OrgzlyDatabase.MIGRATION_157_158)

        // 4. Verify columns exist and default values are NULL using raw query on migrated DB
        val cursor = db.query("SELECT last_sync_action_result, last_sync_action_timestamp FROM books WHERE id = 1")
        assertTrue("Cursor should contain results", cursor.moveToFirst())
        // Check column existence implicitly by querying. Check default NULL value.
        assertNull("Default last_sync_action_result should be null", cursor.getString(cursor.getColumnIndexOrThrow("last_sync_action_result")))
        assertTrue("Default last_sync_action_timestamp should be null", cursor.isNull(cursor.getColumnIndexOrThrow("last_sync_action_timestamp")))
        cursor.close()

        // Close the SupportSQLiteDatabase handle from the helper
        db.close()

        // 5. Optional: Verify DAO update works on migrated schema
        // Re-open the database with Room to get DAO access after migration
        val roomDb = OrgzlyDatabase.forFile(InstrumentationRegistry.getInstrumentation().targetContext, TEST_DB) // Use TEST_DB name
        val bookDao = roomDb.book()
        val testTimestamp = System.currentTimeMillis()
        val updateCount = bookDao.updateLastSyncActionResult(1, SyncResult.SUCCESS, testTimestamp)
        assertEquals("DAO update should affect 1 row", 1, updateCount)

        // Re-query via DAO to check if update worked
        val book = bookDao.get(1)
        assertNotNull("Book should be found", book)
        assertEquals(SyncResult.SUCCESS, book?.lastSyncActionResult)
        assertEquals(testTimestamp, book?.lastSyncActionTimestamp)

        // Close the Room database instance
        roomDb.close()
    }

} 