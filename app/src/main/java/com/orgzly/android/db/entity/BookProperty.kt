package com.orgzly.android.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
        tableName = "book_properties",

        primaryKeys = [ "book_id", "name" ],

        foreignKeys = [
            ForeignKey(
                    entity = Book::class,
                    parentColumns = arrayOf("id"),
                    childColumns = arrayOf("book_id"),
                    onDelete = ForeignKey.CASCADE)
        ],

        indices = [
            Index("book_id"),
            Index("name"),
            Index("value")
        ]
)
data class BookProperty(
        @ColumnInfo(name = "book_id")
        val bookId: Long,

        val name: String,

        val value: String
)
