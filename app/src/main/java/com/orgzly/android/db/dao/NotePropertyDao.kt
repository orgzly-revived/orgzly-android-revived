package com.orgzly.android.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.orgzly.android.db.entity.NoteProperty

@Dao
abstract class NotePropertyDao : BaseDao<NoteProperty> {

    @Query("SELECT * FROM note_properties WHERE note_id = :noteId ORDER BY position")
    abstract fun get(noteId: Long): List<NoteProperty>

    @Query("SELECT * FROM note_properties WHERE note_id = :noteId AND name = :name ORDER BY position")
    abstract fun get(noteId: Long, name: String): List<NoteProperty>

    /** The note's own value, else the nearest ancestor's. Null when no ancestor sets it. */
    @Query("""
        SELECT p.value
        FROM note_properties p
        JOIN notes n ON (n.id = p.note_id)
        WHERE p.name = :name COLLATE NOCASE AND (
            p.note_id = :noteId
            OR p.note_id IN (SELECT ancestor_note_id FROM note_ancestors WHERE note_id = :noteId))
        ORDER BY n.level DESC
        LIMIT 1
    """)
    abstract fun getInherited(noteId: Long, name: String): String?

    /** The nearest ancestor's value, ignoring the note's own. */
    @Query("""
        SELECT p.value
        FROM note_properties p
        JOIN notes n ON (n.id = p.note_id)
        WHERE p.name = :name COLLATE NOCASE
            AND p.note_id IN (SELECT ancestor_note_id FROM note_ancestors WHERE note_id = :noteId)
        ORDER BY n.level DESC
        LIMIT 1
    """)
    abstract fun getInheritedFromAncestors(noteId: Long, name: String): String?

    @Query("SELECT name FROM note_properties GROUP BY LOWER(name)")
    abstract fun allDistinctNames(): List<String>

    @Query("SELECT * FROM note_properties")
    abstract fun getAll(): List<NoteProperty>

    @Transaction
    open fun upsert(noteId: Long, name: String, value: String) {
        val properties = get(noteId, name)

        if (properties.isEmpty()) {
            // Insert new
            val position = getNextAvailablePosition(noteId)
            insert(NoteProperty(noteId, position, name, value))

        } else {
            // Update first
            update(properties.first().copy(value = value))

            // Delete others
            for (i in 1 until properties.size) {
                delete(properties[i])
            }
        }
    }

    private fun getNextAvailablePosition(noteId: Long): Int {
        return getLastPosition(noteId).let {
            if (it != null) it + 1 else 1
        }
    }

    @Query("SELECT MAX(position) FROM note_properties WHERE note_id = :noteId")
    abstract fun getLastPosition(noteId: Long): Int?

    @Query("DELETE FROM note_properties WHERE note_id = :noteId")
    abstract fun delete(noteId: Long)
}
