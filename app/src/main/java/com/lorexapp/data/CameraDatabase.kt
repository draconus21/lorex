package com.lorexapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lorexapp.model.Camera

@Database(entities = [Camera::class], version = 2, exportSchema = false)
abstract class CameraDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao

    companion object {
        @Volatile private var INSTANCE: CameraDatabase? = null

        fun getDatabase(context: Context): CameraDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    CameraDatabase::class.java,
                    "lorexcam_database"
                )
                .fallbackToDestructiveMigration() // dev only — bumped schema for is4K field
                .build().also { INSTANCE = it }
            }
    }
}
