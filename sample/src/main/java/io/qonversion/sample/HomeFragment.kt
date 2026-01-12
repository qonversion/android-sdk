package io.qonversion.sample

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QUser
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import io.qonversion.sample.databinding.FragmentHomeBinding
import kotlin.system.exitProcess

private const val TAG = "HomeFragment"
private const val CLICK_TIMEOUT = 500L
private const val REQUIRED_CLICKS = 5

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var clickCount = 0
    private var lastClickTime = 0L
    private val clickHandler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { resetClickCount() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupImageViewClickListener()
        loadUserInfo()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadUserInfo() {
        binding.initIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorGray))
        binding.initStatusText.text = getString(R.string.initializing)

        Qonversion.shared.userInfo(object : QonversionUserCallback {
            override fun onSuccess(user: QUser) {
                _binding?.let { b ->
                    b.initIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorGreen))
                    b.initStatusText.text = getString(R.string.initialization_successful)
                    b.qonversionIdValue.text = user.qonversionId
                    b.identityIdValue.text = user.identityId ?: getString(R.string.anonymous)
                    b.userInfoContainer.visibility = View.VISIBLE
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.initIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.colorRed))
                    b.initStatusText.text = getString(R.string.initialization_error)
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun setupImageViewClickListener() {
        binding.imageView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > CLICK_TIMEOUT) {
                resetClickCount()
            }

            clickCount++
            lastClickTime = currentTime

            clickHandler.removeCallbacks(clickRunnable)
            clickHandler.postDelayed(clickRunnable, CLICK_TIMEOUT)

            if (clickCount >= REQUIRED_CLICKS) {
                showConfigurationDialog()
                resetClickCount()
            }
        }
    }

    private fun resetClickCount() {
        clickCount = 0
        lastClickTime = 0
    }

    private fun showConfigurationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_configuration, null)
        val projectKeyInput = dialogView.findViewById<EditText>(R.id.projectKeyInput)
        val apiRadioGroup = dialogView.findViewById<RadioGroup>(R.id.apiRadioGroup)
        val customUrlInput = dialogView.findViewById<EditText>(R.id.customUrlInput)

        customUrlInput.visibility = View.GONE

        apiRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            customUrlInput.visibility = if (checkedId == R.id.radioCustom) View.VISIBLE else View.GONE
        }

        val currentProjectKey = getProjectKey(requireContext(), "")
        val currentApiUrl = getApiUrl(requireContext())

        projectKeyInput.setText(currentProjectKey)
        if (currentApiUrl != null) {
            apiRadioGroup.check(R.id.radioCustom)
            customUrlInput.setText(currentApiUrl)
            customUrlInput.visibility = View.VISIBLE
        } else {
            apiRadioGroup.check(R.id.radioProduction)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.qonversion_configuration))
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.apply)) { _, _ ->
                val projectKey = projectKeyInput.text.toString()
                val apiUrl = customUrlInput.takeIf {
                    apiRadioGroup.checkedRadioButtonId == R.id.radioCustom
                }?.text?.toString()
                storeQonversionPrefs(requireContext(), projectKey, apiUrl)
                exitProcess(0)
            }
            .setNeutralButton(getString(R.string.reset)) { _, _ ->
                storeQonversionPrefs(requireContext(), "", null)
                exitProcess(0)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
