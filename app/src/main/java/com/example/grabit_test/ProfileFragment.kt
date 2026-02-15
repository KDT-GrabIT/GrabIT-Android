package com.example.grabitTest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.grabitTest.databinding.FragmentProfileBinding
import com.example.grabitTest.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 내 정보 화면.
 * [자주 찾는 상품] [최근 찾은 상품] [사용방법] → 클릭 시 상세/다이얼로그.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var helpTtsManager: TTSManager? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.themeSwitch.isChecked = ThemeHelper.isLightMode(requireContext())
        binding.themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            ThemeHelper.setThemeMode(requireContext(), if (isChecked) ThemeHelper.MODE_LIGHT else ThemeHelper.MODE_DARK)
            ThemeHelper.applyTheme(requireContext())
            requireActivity().recreate()
        }

        binding.btnHelp.setOnClickListener { showHelpDialog() }

        binding.btnFrequent.setOnClickListener {
            findNavController().navigate(
                R.id.nav_search_history_detail,
                bundleOf(SEARCH_HISTORY_DETAIL_TYPE to "frequent")
            )
        }
        binding.btnRecent.setOnClickListener {
            findNavController().navigate(
                R.id.nav_search_history_detail,
                bundleOf(SEARCH_HISTORY_DETAIL_TYPE to "recent")
            )
        }
    }

    private fun showHelpDialog() {
        val message = VoiceFlowController.MSG_HELP
        val view = layoutInflater.inflate(R.layout.dialog_help, null)
        (view.findViewById(R.id.helpText) as android.widget.TextView).text = message

        helpTtsManager?.release()
        helpTtsManager = TTSManager(
            context = requireContext().applicationContext,
            onReady = { },
            onSpeakDone = { },
            onError = { }
        )
        helpTtsManager?.init { success ->
            if (success) {
                requireActivity().runOnUiThread {
                    helpTtsManager?.speak(message, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null)
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("사용방법")
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ -> helpTtsManager?.release(); helpTtsManager = null }
            .setOnDismissListener { helpTtsManager?.release(); helpTtsManager = null }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        helpTtsManager?.release()
        helpTtsManager = null
        _binding = null
    }
}
