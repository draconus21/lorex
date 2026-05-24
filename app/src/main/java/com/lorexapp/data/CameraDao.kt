package com.lorexapp.data

import androidx.room.*
import com.lorexapp.model.Camera
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras ORDER BY sortOrder ASC, id ASC")
    fun getAllCameras(): Flow<List<Camera>>

    @Query("SELECT * FROM cameras WHERE id = :id")
    suspend fun getCameraById(id: Int): Camera?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCamera(camera: Camera): Long

    @Update
    suspend fun updateCamera(camera: Camera)

    @Delete
    suspend fun deleteCamera(camera: Camera)

    @Query("UPDATE cameras SET thumbnailPath = :path WHERE id = :id")
    suspend fun updateThumbnail(id: Int, path: String)

    @Query("UPDATE cameras SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Int, order: Int)

    @Query("SELECT * FROM cameras ORDER BY id DESC LIMIT 1")
    suspend fun getLastCamera(): Camera?

    @Query("SELECT COUNT(*) FROM cameras")
    suspend fun cameraCount(): Int
}
