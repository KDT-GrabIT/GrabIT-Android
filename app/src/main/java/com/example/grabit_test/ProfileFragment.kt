package com.example.grabitTest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.grabitTest.databinding.FragmentProfileBinding
import com.example.grabitTest.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 내 정보 화면.
 * [자주 찾는 상품] [최근 찾은 상품] [사용방법] → 클릭 시 상세/다이얼로그.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var helpTtsManager: TTSManager? = null
    private var speakerVerificationManager: SpeakerVerificationManager? = null
    private var lastSimilarity: Float? = null
    private var lastDecision: Boolean? = null

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
        speakerVerificationManager = SpeakerVerificationManager(requireContext())

        binding.themeSwitch.isChecked = ThemeHelper.isLightMode(requireContext())
        binding.themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            ThemeHelper.setThemeMode(requireContext(), if (isChecked) ThemeHelper.MODE_LIGHT else ThemeHelper.MODE_DARK)
            ThemeHelper.applyTheme(requireContext())
            requireActivity().recreate()
        }

        binding.btnHelp.setOnClickListener { showHelpDialog() }
        binding.speakerPanelTitle.text = "\uD654\uC790 \uAC80\uC99D"
        binding.speakerFallbackHintText.text =
            "\uB0B4 \uC815\uBCF4\uC5D0\uC11C \uD654\uC790 \uB4F1\uB85D\uC744 \uD558\uBA74 \uC74C\uC131 \uBA85\uB839 \uBCF4\uD638\uAC00 \uC801\uC6A9\uB429\uB2C8\uB2E4."
        binding.speakerEnrollBtn.text = "\uD654\uC790 \uB4F1\uB85D \uC2DC\uC791"
        binding.speakerVerifyBtn.text = "\uD654\uC790 \uAC80\uC99D \uD14C\uC2A4\uD2B8"
        binding.speakerResetBtn.text = "\uD654\uC790 \uB4F1\uB85D \uCD08\uAE30\uD654"
        binding.speakerEnrollBtn.setOnClickListener { startSpeakerEnrollment() }
        binding.speakerVerifyBtn.setOnClickListener { runSpeakerVerificationTest() }
        binding.speakerResetBtn.setOnClickListener { resetSpeakerEnrollment() }
        updateSpeakerVerificationStatus()

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

    override fun onResume() {
        super.onResume()
        updateSpeakerVerificationStatus()
    }

    private fun startSpeakerEnrollment() {
        if (!hasRecordAudioPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO)
            Toast.makeText(requireContext(), "\uB9C8\uC774\uD06C \uAD8C\uD55C\uC774 \uD544\uC694\uD569\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
            return
        }
        val verifier = speakerVerificationManager ?: return
        setSpeakerButtonsEnabled(false)
        binding.speakerStatusText.text = "\uD654\uC790 \uB4F1\uB85D \uC0C1\uD0DC: \uB4F1\uB85D \uC911"
        binding.speakerDecisionText.text = "\uB9C8\uC9C0\uB9C9 \uAC80\uC99D \uACB0\uACFC: -"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                verifier.enrollFromMicrophoneWithStats { sample ->
                    requireActivity().runOnUiThread {
                        if (_binding != null) {
                            binding.speakerStatusText.text =
                                "\uD654\uC790 \uB4F1\uB85D \uC0C1\uD0DC: \uB4F1\uB85D \uC911 (${sample.index}/3)"
                        }
                    }
                }
                lastSimilarity = null
                lastDecision = null
                if (_binding != null) {
                    updateSpeakerVerificationStatus()
                    Toast.makeText(requireContext(), "\uD654\uC790 \uB4F1\uB85D \uC644\uB8CC", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(SpeakerVerificationConfig.LOG_TAG, "SV_REGISTER_FAILED", e)
                if (_binding != null) {
                    binding.speakerStatusText.text = "\uD654\uC790 \uB4F1\uB85D \uC0C1\uD0DC: \uB4F1\uB85D \uC2E4\uD328"
                    Toast.makeText(requireContext(), "\uD654\uC790 \uB4F1\uB85D \uC2E4\uD328", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (_binding != null) setSpeakerButtonsEnabled(true)
            }
        }
    }

    private fun runSpeakerVerificationTest() {
        if (!hasRecordAudioPermission()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD_AUDIO)
            Toast.makeText(requireContext(), "\uB9C8\uC774\uD06C \uAD8C\uD55C\uC774 \uD544\uC694\uD569\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
            return
        }
        val verifier = speakerVerificationManager ?: return
        if (!verifier.hasVoiceprint()) {
            updateSpeakerVerificationStatus()
            Toast.makeText(requireContext(), "\uD654\uC790\uAC00 \uB4F1\uB85D\uB418\uC9C0 \uC54A\uC558\uC2B5\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
            return
        }
        setSpeakerButtonsEnabled(false)
        binding.speakerDecisionText.text = "\uB9C8\uC9C0\uB9C9 \uAC80\uC99D \uACB0\uACFC: \uAC80\uC99D \uC911"
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = verifier.verifyFromMicrophone(SpeakerVerificationConfig.DEFAULT_THRESHOLD)
                lastSimilarity = result.score
                lastDecision = result.accepted
                if (_binding != null) {
                    updateSpeakerVerificationStatus()
                    Toast.makeText(
                        requireContext(),
                        if (result.accepted) "\uD1B5\uACFC" else "\uAC70\uBD80",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(SpeakerVerificationConfig.LOG_TAG, "SV_VERIFY_TEST_FAILED", e)
                if (_binding != null) {
                    binding.speakerDecisionText.text = "\uB9C8\uC9C0\uB9C9 \uAC80\uC99D \uACB0\uACFC: \uAC80\uC99D \uC2E4\uD328"
                    Toast.makeText(requireContext(), "\uD654\uC790 \uAC80\uC99D \uC2E4\uD328", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (_binding != null) setSpeakerButtonsEnabled(true)
            }
        }
    }

    private fun resetSpeakerEnrollment() {
        speakerVerificationManager?.clearVoiceprint()
        Log.i(SpeakerVerificationConfig.LOG_TAG, "SV_REGISTER_RESET")
        lastSimilarity = null
        lastDecision = null
        updateSpeakerVerificationStatus()
        Toast.makeText(requireContext(), "\uD654\uC790 \uB4F1\uB85D \uCD08\uAE30\uD654", Toast.LENGTH_SHORT).show()
    }

    private fun updateSpeakerVerificationStatus() {
        if (_binding == null) return
        val registered = speakerVerificationManager?.hasVoiceprint() == true
        binding.speakerStatusText.text =
            "\uD654\uC790 \uB4F1\uB85D \uC0C1\uD0DC: " +
                (if (registered) "\uB4F1\uB85D \uC644\uB8CC" else "\uBBF8\uB4F1\uB85D")
        binding.speakerSimilarityText.text =
            "\uB9C8\uC9C0\uB9C9 \uAC80\uC99D similarity: ${lastSimilarity?.let { formatFloat(it) } ?: "-"}"
        binding.speakerDecisionText.text =
            "\uB9C8\uC9C0\uB9C9 \uAC80\uC99D \uACB0\uACFC: " +
                (when (lastDecision) {
                    true -> "\uD1B5\uACFC"
                    false -> "\uAC70\uBD80"
                    null -> "-"
                })
        binding.speakerThresholdText.text =
            "\uD604\uC7AC threshold: ${formatFloat(SpeakerVerificationConfig.DEFAULT_THRESHOLD)}"
    }

    private fun setSpeakerButtonsEnabled(enabled: Boolean) {
        binding.speakerEnrollBtn.isEnabled = enabled
        binding.speakerVerifyBtn.isEnabled = enabled
        binding.speakerResetBtn.isEnabled = enabled
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun formatFloat(value: Float): String =
        if (value.isNaN()) "NaN" else String.format(Locale.US, "%.6f", value)

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
        speakerVerificationManager?.close()
        speakerVerificationManager = null
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_RECORD_AUDIO = 21
    }
}
