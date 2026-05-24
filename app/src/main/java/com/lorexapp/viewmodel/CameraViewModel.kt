package com.lorexapp.viewmodel

import androidx.lifecycle.*
import com.lorexapp.data.CameraRepository
import com.lorexapp.model.Camera
import kotlinx.coroutines.launch

class CameraViewModel(private val repository: CameraRepository) : ViewModel() {

    val cameras: LiveData<List<Camera>> = repository.allCameras.asLiveData()

    fun insert(camera: Camera) = viewModelScope.launch { repository.insert(camera) }
    fun update(camera: Camera) = viewModelScope.launch { repository.update(camera) }
    fun delete(camera: Camera) = viewModelScope.launch { repository.delete(camera) }

    fun updateThumbnail(id: Int, path: String) =
        viewModelScope.launch { repository.updateThumbnail(id, path) }

    suspend fun getById(id: Int): Camera? = repository.getById(id)
}

class CameraViewModelFactory(private val repository: CameraRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CameraViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
