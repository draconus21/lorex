package com.lorexapp.data

import com.lorexapp.model.Camera
import kotlinx.coroutines.flow.Flow

class CameraRepository(private val dao: CameraDao) {
    val allCameras: Flow<List<Camera>> = dao.getAllCameras()

    suspend fun getById(id: Int): Camera? = dao.getCameraById(id)
    suspend fun getLastCamera(): Camera? = dao.getLastCamera()
    suspend fun insert(camera: Camera): Long = dao.insertCamera(camera)
    suspend fun update(camera: Camera) = dao.updateCamera(camera)
    suspend fun delete(camera: Camera) = dao.deleteCamera(camera)
    suspend fun updateThumbnail(id: Int, path: String) = dao.updateThumbnail(id, path)
    suspend fun count(): Int = dao.cameraCount()
}
