package com.lorexapp.ui.multistream

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lorexapp.databinding.ItemFaceEntryBinding

data class FaceEntry(
    val face: Bitmap,
    val cameraName: String,
    val time: String
)

class FaceEntryAdapter : RecyclerView.Adapter<FaceEntryAdapter.FH>() {

    private val items = mutableListOf<FaceEntry>()

    /** Prepend new faces and cap the list at 50 to avoid memory bloat */
    fun addFaces(newFaces: List<FaceEntry>) {
        items.addAll(0, newFaces)
        while (items.size > 50) items.removeLast()
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FH(ItemFaceEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FH, position: Int) = holder.bind(items[position])

    inner class FH(private val b: ItemFaceEntryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: FaceEntry) {
            b.ivFace.setImageBitmap(e.face)
            b.tvFaceCamera.text = e.cameraName
            b.tvFaceTime.text = e.time
        }
    }
}
