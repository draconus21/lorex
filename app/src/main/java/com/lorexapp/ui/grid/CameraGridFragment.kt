package com.lorexapp.ui.grid

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.lorexapp.LorexApp
import com.lorexapp.R
import com.lorexapp.databinding.FragmentCameraGridBinding
import com.lorexapp.model.Camera
import com.lorexapp.ui.add.AddCameraActivity
import com.lorexapp.ui.multistream.MultiStreamActivity
import com.lorexapp.ui.stream.CameraStreamActivity
import com.lorexapp.viewmodel.CameraViewModel
import com.lorexapp.viewmodel.CameraViewModelFactory

class CameraGridFragment : Fragment() {

    private var _binding: FragmentCameraGridBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CameraViewModel by activityViewModels {
        (requireActivity().application as LorexApp).viewModelFactory
    }

    private lateinit var adapter: CameraGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeCameras()

        binding.fabAddCamera.setOnClickListener {
            startActivity(Intent(requireContext(), AddCameraActivity::class.java))
        }

        binding.btnLiveView.setOnClickListener {
            MultiStreamActivity.start(requireContext())
        }

        // Grid column selector (2 or 3 column)
        binding.btnGridToggle.setOnClickListener { toggleGridColumns() }
    }

    private fun setupRecyclerView() {
        adapter = CameraGridAdapter(
            onCameraClick = { camera -> openStream(camera) },
            onCameraLongClick = { camera, anchor -> showContextMenu(camera, anchor) }
        )
        binding.rvCameras.adapter = adapter
        binding.rvCameras.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvCameras.setHasFixedSize(true)
    }

    private fun observeCameras() {
        viewModel.cameras.observe(viewLifecycleOwner) { cameras ->
            adapter.submitList(cameras)
            binding.tvEmpty.visibility = if (cameras.isEmpty()) View.VISIBLE else View.GONE
            binding.rvCameras.visibility = if (cameras.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun openStream(camera: Camera) {
        val intent = Intent(requireContext(), CameraStreamActivity::class.java).apply {
            putExtra(CameraStreamActivity.EXTRA_CAMERA_ID, camera.id)
        }
        startActivity(intent)
    }

    private fun showContextMenu(camera: Camera, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_camera_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    val intent = Intent(requireContext(), AddCameraActivity::class.java).apply {
                        putExtra(AddCameraActivity.EXTRA_CAMERA_ID, camera.id)
                    }
                    startActivity(intent)
                    true
                }
                R.id.action_delete -> {
                    confirmDelete(camera)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun confirmDelete(camera: Camera) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Camera")
            .setMessage("Remove \"${camera.displayLabel()}\" from your list?")
            .setPositiveButton("Delete") { _, _ -> viewModel.delete(camera) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var currentColumns = 2
    private fun toggleGridColumns() {
        currentColumns = if (currentColumns == 2) 3 else 2
        (binding.rvCameras.layoutManager as GridLayoutManager).spanCount = currentColumns
        binding.btnGridToggle.setImageResource(
            if (currentColumns == 2) R.drawable.ic_grid_3 else R.drawable.ic_grid_2
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
