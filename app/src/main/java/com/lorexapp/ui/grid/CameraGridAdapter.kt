package com.lorexapp.ui.grid

import android.net.Uri
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.lorexapp.R
import com.lorexapp.databinding.ItemCameraGridBinding
import com.lorexapp.model.Camera
import java.io.File

class CameraGridAdapter(
    private val onCameraClick: (Camera) -> Unit,
    private val onCameraLongClick: (Camera, View) -> Unit
) : ListAdapter<Camera, CameraGridAdapter.CameraViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val binding = ItemCameraGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CameraViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CameraViewHolder(
        private val binding: ItemCameraGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(camera: Camera) {
            binding.tvCameraName.text = camera.displayLabel()
            binding.tvChannel.text = "Ch ${camera.channel}"

            // Load thumbnail if cached, else show placeholder
            val thumbFile = camera.thumbnailPath?.let { File(it) }
            if (thumbFile != null && thumbFile.exists()) {
                binding.ivThumbnail.load(thumbFile) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(8f))
                    placeholder(R.drawable.ic_camera_placeholder)
                }
            } else {
                binding.ivThumbnail.setImageResource(R.drawable.ic_camera_placeholder)
            }

            // Status indicator
            binding.viewStatus.setBackgroundResource(
                if (camera.isEnabled) R.drawable.bg_status_online
                else R.drawable.bg_status_offline
            )

            binding.root.setOnClickListener { onCameraClick(camera) }
            binding.root.setOnLongClickListener { v ->
                onCameraLongClick(camera, v)
                true
            }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Camera>() {
            override fun areItemsTheSame(old: Camera, new: Camera) = old.id == new.id
            override fun areContentsTheSame(old: Camera, new: Camera) = old == new
        }
    }
}
