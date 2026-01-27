package com.example.grabit.ui

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.grabit.R
import com.example.grabit.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 권한 요청 대행사 (결과를 받아주는 녀석)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 카메라 권한이 허용되었는지 확인
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            // 허용됐으면 카메라 화면으로 이동
            findNavController().navigate(R.id.action_home_to_camera)
        } else {
            Toast.makeText(requireContext(), "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 버튼 누르면 권한 체크 시작
        binding.btnStart.setOnClickListener {
            checkPermissionsAndRun()
        }
    }

    private fun checkPermissionsAndRun() {
        requestPermissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}