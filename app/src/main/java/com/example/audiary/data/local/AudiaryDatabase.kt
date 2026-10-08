package com.example.audiary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteEntity::class, SongEntity::class, FavoriteEntity::class, AppFlag::class],
    version = 1, exportSchema = true)
abstract class AudiaryDatabase : RoomDatabase() {
    abstract fun dao(): AudiaryDao
    companion object {
        fun create(context: Context): AudiaryDatabase = Room.databaseBuilder(
            context.applicationContext, AudiaryDatabase::class.java, "audiary.db"
        ).build() // Schema changes must supply migrations; never delete personal data as a fallback.
    }
}
