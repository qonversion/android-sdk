package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QRemoteConfig
import com.qonversion.android.sdk.dto.QRemoteConfigList
import com.qonversion.android.sdk.listeners.QonversionExperimentAttachCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigCallback
import com.qonversion.android.sdk.listeners.QonversionRemoteConfigListCallback
import io.qonversion.sample.databinding.FragmentRemoteConfigsBinding

private const val TAG = "RemoteConfigsFragment"

class RemoteConfigsFragment : Fragment() {

    private var _binding: FragmentRemoteConfigsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemoteConfigsBinding.inflate(inflater, container, false)

        binding.recyclerViewConfigs.layoutManager = LinearLayoutManager(context)
        setupButtons()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupButtons() {
        binding.buttonGetRemoteConfigList.setOnClickListener {
            val contextKeys = binding.editContextKeys.text.toString()
            if (contextKeys.isNotEmpty()) {
                val keys = contextKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                getRemoteConfigList(keys)
            } else {
                getRemoteConfigList()
            }
        }

        binding.buttonGetRemoteConfig.setOnClickListener {
            val contextKey = binding.editSingleContextKey.text.toString()
            getRemoteConfig(contextKey.ifEmpty { null })
        }

        binding.buttonAttachExperiment.setOnClickListener {
            val experimentId = binding.editExperimentId.text.toString()
            val groupId = binding.editGroupId.text.toString()
            if (experimentId.isEmpty() || groupId.isEmpty()) {
                Toast.makeText(context, getString(R.string.please_enter_experiment_and_group), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            attachToExperiment(experimentId, groupId)
        }

        binding.buttonDetachExperiment.setOnClickListener {
            val experimentId = binding.editExperimentId.text.toString()
            if (experimentId.isEmpty()) {
                Toast.makeText(context, getString(R.string.please_enter_experiment_id), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            detachFromExperiment(experimentId)
        }
    }

    private fun getRemoteConfigList(contextKeys: List<String>? = null) {
        binding.progressBar.visibility = View.VISIBLE

        val callback = object : QonversionRemoteConfigListCallback {
            override fun onSuccess(remoteConfigList: QRemoteConfigList) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    displayRemoteConfigs(remoteConfigList.remoteConfigs)
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        }

        if (contextKeys != null && contextKeys.isNotEmpty()) {
            Qonversion.shared.remoteConfigList(contextKeys, true, callback)
        } else {
            Qonversion.shared.remoteConfigList(callback)
        }
    }

    private fun getRemoteConfig(contextKey: String?) {
        binding.progressBar.visibility = View.VISIBLE

        val callback = object : QonversionRemoteConfigCallback {
            override fun onSuccess(remoteConfig: QRemoteConfig) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    displayRemoteConfigs(listOf(remoteConfig))
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        }

        if (contextKey != null) {
            Qonversion.shared.remoteConfig(contextKey, callback)
        } else {
            Qonversion.shared.remoteConfig(callback)
        }
    }

    private fun displayRemoteConfigs(configs: List<QRemoteConfig>) {
        if (configs.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.recyclerViewConfigs.visibility = View.GONE
        } else {
            binding.emptyStateText.visibility = View.GONE
            binding.recyclerViewConfigs.visibility = View.VISIBLE
            binding.recyclerViewConfigs.adapter = RemoteConfigsAdapter(configs)
        }
    }

    private fun attachToExperiment(experimentId: String, groupId: String) {
        binding.progressBar.visibility = View.VISIBLE
        Qonversion.shared.attachUserToExperiment(experimentId, groupId, object : QonversionExperimentAttachCallback {
            override fun onSuccess() {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                Toast.makeText(context, getString(R.string.attached_to_experiment), Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun detachFromExperiment(experimentId: String) {
        binding.progressBar.visibility = View.VISIBLE
        Qonversion.shared.detachUserFromExperiment(experimentId, object : QonversionExperimentAttachCallback {
            override fun onSuccess() {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                Toast.makeText(context, getString(R.string.detached_from_experiment), Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        })
    }
}
