package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.qonversion.nocodes.NoCodes
import io.qonversion.nocodes.dto.NoCodesTheme
import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.dto.QNoCodeScreen
import io.qonversion.nocodes.dto.QScreenVariableValue
import io.qonversion.nocodes.error.NoCodesError
import io.qonversion.nocodes.interfaces.CustomVariablesDelegate
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.nocodes.interfaces.NoCodesScreenLoadCallback
import io.qonversion.sample.databinding.FragmentNocodesBinding

class NoCodesFragment : Fragment(), NoCodesDelegate, CustomVariablesDelegate {

    private var _binding: FragmentNocodesBinding? = null
    private val binding get() = _binding!!

    private val events = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNocodesBinding.inflate(inflater, container, false)

        setupButtons()
        setupNoCodes()

        // Restore last context key
        binding.editContextKey.setText(getLastContextKey(requireContext()))

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupButtons() {
        binding.buttonShowScreen.setOnClickListener {
            val contextKey = binding.editContextKey.text.toString()
            if (contextKey.isNotEmpty()) {
                saveLastContextKey(requireContext(), contextKey)
                showScreen(contextKey)
            } else {
                Toast.makeText(context, getString(R.string.please_enter_context_key), Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonLoadThenShowScreen.setOnClickListener {
            val contextKey = binding.editContextKey.text.toString()
            if (contextKey.isNotEmpty()) {
                saveLastContextKey(requireContext(), contextKey)
                loadThenShowScreen(contextKey)
            } else {
                Toast.makeText(context, getString(R.string.please_enter_context_key), Toast.LENGTH_SHORT).show()
            }
        }

        binding.buttonClose.setOnClickListener {
            NoCodes.shared.close()
            addEvent(getString(R.string.nocodes_closed))
        }

        binding.buttonClearEvents.setOnClickListener {
            events.clear()
            updateEventsDisplay()
        }

        // Theme radio buttons
        binding.radioThemeAuto.isChecked = true // Default
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioThemeAuto -> NoCodesTheme.Auto
                R.id.radioThemeLight -> NoCodesTheme.Light
                R.id.radioThemeDark -> NoCodesTheme.Dark
                else -> NoCodesTheme.Auto
            }
            NoCodes.shared.setTheme(theme)
            addEvent(getString(R.string.theme_set, theme.name))
        }
    }

    private fun setupNoCodes() {
        NoCodes.shared.setDelegate(this)
        NoCodes.shared.setCustomVariablesDelegate(this)
        addEvent(getString(R.string.nocodes_delegate_set))
    }

    // CustomVariablesDelegate
    override fun getCustomVariables(contextKey: String): Map<String, String> {
        val variables = mapOf("custom_var" to "super")
        android.util.Log.d("NoCodes", "Custom variables requested for $contextKey: $variables")
        return variables
    }

    private fun showScreen(contextKey: String) {
        addEvent(getString(R.string.showing_screen, contextKey))
        NoCodes.shared.showScreen(contextKey)
    }

    private fun loadThenShowScreen(contextKey: String) {
        // Ask-first gate: check the screen's availability before anything is presented,
        // so on failure we can show our own fallback UI instead of the SDK skeleton.
        NoCodes.shared.loadScreen(contextKey, object : NoCodesScreenLoadCallback {
            override fun onSuccess(screen: QNoCodeScreen) {
                addEvent(getString(R.string.screen_loaded_presenting, screen.id))

                // The loaded entity carries the typed default variables configured in the
                // builder — authored custom variables and product slots — readable by key
                // (e.g. screen.defaultVariable("show_trial")) before anything is presented.
                val variables = screen.defaultVariables.joinToString(", ") { variable ->
                    "${variable.kind} ${variable.key} = ${formatVariableValue(variable.value)}"
                }
                addEvent(getString(R.string.screen_default_variables, variables))

                NoCodes.shared.showScreen(contextKey)
            }

            override fun onError(error: NoCodesError) {
                // The SDK skeleton never appeared, so the app can show its own fallback UI here.
                addEvent(getString(R.string.screen_load_failed_fallback, error.code.toString()))
            }
        })
    }

    private fun addEvent(event: String) {
        events.add(0, event)
        updateEventsDisplay()
    }

    private fun updateEventsDisplay() {
        if (events.isEmpty()) {
            binding.eventsText.text = getString(R.string.no_events_yet)
        } else {
            binding.eventsText.text = events.joinToString("\n\n")
        }
    }

    private fun formatVariableValue(value: QScreenVariableValue): String = when (value) {
        is QScreenVariableValue.Bool -> value.value.toString()
        is QScreenVariableValue.Str -> "\"${value.value}\""
        is QScreenVariableValue.Num -> value.value.toString()
        QScreenVariableValue.None -> "null"
    }

    // NoCodesDelegate implementation
    override fun onScreenShown(screenId: String) {
        addEvent(getString(R.string.screen_shown, screenId))
    }

    override fun onScreenFailedToLoad(error: NoCodesError) {
        addEvent(getString(R.string.screen_failed_to_load, error.details ?: error.code.toString()))
        NoCodes.shared.close()
    }

    override fun onActionStartedExecuting(action: QAction) {
        addEvent(getString(R.string.action_started, action.type.name))
    }

    override fun onActionFinishedExecuting(action: QAction) {
        addEvent(getString(R.string.action_finished, action.type.name))
    }

    override fun onActionFailedToExecute(action: QAction) {
        addEvent(getString(R.string.action_failed, action.type.name))
    }

    override fun onFinished() {
        addEvent(getString(R.string.flow_finished))
    }
}
