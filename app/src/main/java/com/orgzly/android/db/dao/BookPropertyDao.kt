package com.orgzly.android.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.orgzly.android.db.entity.BookProperty

@Dao
abstract class BookPropertyDao : BaseDao<BookProperty> {

    @Query("SELECT * FROM book_properties WHERE book_id = :bookId")
    abstract fun get(bookId: Long): List<BookProperty>

    @Query("SELECT * FROM book_properties WHERE book_id = :bookId AND name = :name")
    abstract fun get(bookId: Long, name: String): List<BookProperty>

    @Query("SELECT * FROM book_properties")
    abstract fun getAll(): List<BookProperty>

    @Transaction
    open fun upsert(bookId: Long, name: String, value: String) {
        val properties = get(bookId, name)

        if (properties.isEmpty()) {
            // Insert new
            insert(BookProperty(bookId, name, value))

        } else {
            // Update first
            update(properties.first().copy(value = value))

            // Delete others
            for (i in 1 until properties.size) {
                delete(properties[i])
            }
        }
    }

    @Query("DELETE FROM book_properties WHERE book_id = :bookId")
    abstract fun delete(bookId: Long)
}
