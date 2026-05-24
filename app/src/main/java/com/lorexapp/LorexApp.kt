package com.lorexapp

import android.app.Application
import com.lorexapp.data.CameraDatabase
import com.lorexapp.data.CameraRepository
import com.lorexapp.viewmodel.CameraViewModelFactory

class LorexApp : Application() {
    val database by lazy { CameraDatabase.getDatabase(this) }
    val repository by lazy { CameraRepository(database.cameraDao()) }
    val viewModelFactory by lazy { CameraViewModelFactory(repository) }
}
