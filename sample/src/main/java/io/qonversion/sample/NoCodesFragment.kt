package io.qonversion.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.qonversion.nocodes.NoCodes
import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.error.NoCodesError
import io.qonversion.nocodes.interfaces.NoCodesDelegate
import io.qonversion.sample.databinding.FragmentNocodesBinding

private const val TAG = "NoCodesFragment"

class NoCodesFragment : Fragment(), NoCodesDelegate {

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

        binding.buttonClose.setOnClickListener {
            NoCodes.shared.close()
            addEvent(getString(R.string.nocodes_closed))
        }

        binding.buttonClearEvents.setOnClickListener {
            events.clear()
            updateEventsDisplay()
        }
    }

    private fun setupNoCodes() {
        NoCodes.shared.setDelegate(this)
        addEvent(getString(R.string.nocodes_delegate_set))
    }

    private fun showScreen(contextKey: String) {
        addEvent(getString(R.string.showing_screen, contextKey))
        NoCodes.shared.showScreen(contextKey)
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
