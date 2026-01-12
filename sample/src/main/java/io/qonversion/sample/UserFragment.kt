package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.qonversion.android.sdk.Qonversion
import com.qonversion.android.sdk.dto.QAttributionProvider
import com.qonversion.android.sdk.dto.QonversionError
import com.qonversion.android.sdk.dto.QUser
import com.qonversion.android.sdk.dto.properties.QUserProperties
import com.qonversion.android.sdk.dto.properties.QUserPropertyKey
import com.qonversion.android.sdk.listeners.QonversionUserCallback
import com.qonversion.android.sdk.listeners.QonversionUserPropertiesCallback
import io.qonversion.sample.databinding.FragmentUserBinding
import org.json.JSONObject

private const val TAG = "UserFragment"

class UserFragment : Fragment() {

    private var _binding: FragmentUserBinding? = null
    private val binding get() = _binding!!

    private val propertyKeys = listOf(
        "Custom" to null,
        "Email" to QUserPropertyKey.Email,
        "Name" to QUserPropertyKey.Name,
        "Kochava Device Id" to QUserPropertyKey.KochavaDeviceId,
        "AppsFlyer User Id" to QUserPropertyKey.AppsFlyerUserId,
        "Adjust Ad Id" to QUserPropertyKey.AdjustAdId,
        "Facebook Attribution" to QUserPropertyKey.FacebookAttribution,
        "Firebase App Instance Id" to QUserPropertyKey.FirebaseAppInstanceId,
        "AppMetrica Device Id" to QUserPropertyKey.AppMetricaDeviceId,
        "AppMetrica User Profile Id" to QUserPropertyKey.AppMetricaUserProfileId,
        "Pushwoosh Hwid" to QUserPropertyKey.PushWooshHwId,
        "Pushwoosh User Id" to QUserPropertyKey.PushWooshUserId,
        "Advertising Id" to QUserPropertyKey.AdvertisingId,
        "App Set Id" to QUserPropertyKey.AppSetId
    )

    private val attributionProviders = listOf(
        "AppsFlyer" to QAttributionProvider.AppsFlyer,
        "Adjust" to QAttributionProvider.Adjust,
        "Branch" to QAttributionProvider.Branch
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserBinding.inflate(inflater, container, false)

        setupSpinners()
        setupButtons()
        loadUserInfo()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupSpinners() {
        val propertyAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            propertyKeys.map { it.first }
        )
        propertyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPropertyKey.adapter = propertyAdapter

        // Show/hide custom property key field based on selection
        binding.spinnerPropertyKey.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isCustom = propertyKeys[position].second == null
                binding.customPropertyKeyLayout.visibility = if (isCustom) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.customPropertyKeyLayout.visibility = View.VISIBLE
            }
        }

        val attributionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            attributionProviders.map { it.first }
        )
        attributionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAttributionProvider.adapter = attributionAdapter
    }

    private fun setupButtons() {
        binding.buttonIdentify.setOnClickListener {
            val identityId = binding.editIdentityId.text.toString()
            if (identityId.isNotEmpty()) {
                identify(identityId)
            } else {
                Toast.makeText(context, getString(R.string.please_enter_identity_id), Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonLogout.setOnClickListener {
            logout()
        }

        binding.buttonLoadUserInfo.setOnClickListener {
            loadUserInfo()
        }

        binding.buttonLoadUserProperties.setOnClickListener {
            loadUserProperties()
        }

        binding.buttonSetProperty.setOnClickListener {
            setProperty()
        }

        binding.buttonSendAttribution.setOnClickListener {
            sendAttribution()
        }
    }

    private fun identify(identityId: String) {
        binding.progressBar.visibility = View.VISIBLE
        Qonversion.shared.identify(identityId, object : QonversionUserCallback {
            override fun onSuccess(user: QUser) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    updateUserInfo(user)
                    Toast.makeText(context, getString(R.string.user_identified), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun logout() {
        Qonversion.shared.logout()
        loadUserInfo()
        Toast.makeText(context, getString(R.string.user_logged_out), Toast.LENGTH_SHORT).show()
    }

    private fun loadUserInfo() {
        binding.progressBar.visibility = View.VISIBLE
        Qonversion.shared.userInfo(object : QonversionUserCallback {
            override fun onSuccess(user: QUser) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    updateUserInfo(user)
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun updateUserInfo(user: QUser) {
        binding.qonversionIdValue.text = user.qonversionId
        binding.identityIdValue.text = user.identityId ?: getString(R.string.anonymous)
        binding.userInfoContainer.visibility = View.VISIBLE

        val canIdentify = user.identityId == null
        binding.editIdentityId.isEnabled = canIdentify
        binding.buttonIdentify.isEnabled = canIdentify
        binding.buttonLogout.isEnabled = !canIdentify
    }

    private fun loadUserProperties() {
        binding.progressBar.visibility = View.VISIBLE
        Qonversion.shared.userProperties(object : QonversionUserPropertiesCallback {
            override fun onSuccess(userProperties: QUserProperties) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                    displayUserProperties(userProperties)
                }
            }

            override fun onError(error: QonversionError) {
                _binding?.let { b ->
                    b.progressBar.visibility = View.GONE
                }
                showError(requireContext(), error, TAG)
            }
        })
    }

    private fun displayUserProperties(userProperties: QUserProperties) {
        val properties = userProperties.properties
        if (properties.isEmpty()) {
            binding.userPropertiesText.text = getString(R.string.no_properties_set)
        } else {
            val sb = StringBuilder()
            properties.forEach { property ->
                sb.append("${property.key}: ${property.value}\n")
            }
            binding.userPropertiesText.text = sb.toString().trim()
        }
        binding.userPropertiesContainer.visibility = View.VISIBLE
    }

    private fun setProperty() {
        val selectedIndex = binding.spinnerPropertyKey.selectedItemPosition
        val propertyValue = binding.editPropertyValue.text.toString()

        if (propertyValue.isEmpty()) {
            Toast.makeText(context, getString(R.string.please_enter_property_value), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedKey = propertyKeys[selectedIndex].second
        if (selectedKey == null) {
            // Custom property
            val customKey = binding.editCustomPropertyKey.text.toString()
            if (customKey.isEmpty()) {
                Toast.makeText(context, getString(R.string.please_enter_custom_key), Toast.LENGTH_SHORT).show()
                return
            }
            Qonversion.shared.setCustomUserProperty(customKey, propertyValue)
        } else {
            Qonversion.shared.setUserProperty(selectedKey, propertyValue)
        }

        Toast.makeText(context, getString(R.string.property_set_successfully), Toast.LENGTH_SHORT).show()
    }

    private fun sendAttribution() {
        val attributionData = binding.editAttributionData.text.toString()
        if (attributionData.isEmpty()) {
            Toast.makeText(context, getString(R.string.please_enter_attribution_data), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val jsonObject = JSONObject(attributionData)
            val dataMap = mutableMapOf<String, Any>()
            jsonObject.keys().forEach { key ->
                dataMap[key] = jsonObject.get(key)
            }

            val selectedIndex = binding.spinnerAttributionProvider.selectedItemPosition
            val provider = attributionProviders[selectedIndex].second

            Qonversion.shared.attribution(dataMap, provider)
            Toast.makeText(context, getString(R.string.attribution_sent), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.invalid_json_format), Toast.LENGTH_SHORT).show()
        }
    }
}
