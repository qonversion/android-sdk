package com.qonversion.android.sdk.internal.redemption

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * Modal email-input dialog used by [com.qonversion.android.sdk.Qonversion.presentReissueUI]
 * as the fallback when a user cannot complete redemption via the email App Link.
 *
 * Built programmatically (no XML resource) to keep the SDK module's resource
 * surface minimal and avoid host-app theme conflicts. Hosts can wrap this in a
 * standard fragment transaction.
 *
 * The fragment exposes its outcome via [onCompletion]: `true` on a 200 send,
 * `false` if the user cancels.
 *
 * The dialog is not retained across configuration changes — the host is
 * expected to re-present on rotation if needed. This matches the iOS modal's
 * single-shot semantics.
 */
internal class ReissueDialogFragment : DialogFragment() {

    /**
     * Wiring set by [com.qonversion.android.sdk.internal.QonversionInternal] before
     * showing the fragment. Not `Parcelable`, so the dialog cannot survive a
     * process death — by design (see class docs).
     */
    var redemptionManager: RedemptionManager? = null
    var onCompletion: ((Boolean) -> Unit)? = null

    private lateinit var emailInput: EditText
    private lateinit var sendButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var hintText: TextView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setTitle("Restore your purchase")
        dialog.setContentView(buildContent())
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener {
            onCompletion?.invoke(false)
        }
        return dialog
    }

    private fun buildContent(): View {
        val ctx = requireContext()
        val padding = (PADDING_DP * resources.displayMetrics.density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val title = TextView(ctx).apply {
            text = "Enter the email used at checkout to receive a new restore link."
            textSize = TITLE_TEXT_SIZE_SP
        }
        root.addView(title)

        emailInput = EditText(ctx).apply {
            hint = "you@example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setSingleLine(true)
        }
        root.addView(emailInput)

        progress = ProgressBar(ctx).apply {
            visibility = View.GONE
        }
        root.addView(progress)

        hintText = TextView(ctx).apply {
            visibility = View.GONE
            gravity = Gravity.START
        }
        root.addView(hintText)

        sendButton = Button(ctx).apply {
            text = "Send"
            setOnClickListener { onSendClicked() }
        }
        root.addView(sendButton)

        return root
    }

    private fun onSendClicked() {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        if (!isValidEmail(email)) {
            hintText.text = "Please enter a valid email."
            hintText.visibility = View.VISIBLE
            return
        }

        val manager = redemptionManager
        if (manager == null) {
            // Defensive — should never happen because Qonversion wires this
            // up before show(). Surface as a generic error so the user can
            // close the dialog and retry.
            hintText.text = "Something went wrong, please try again."
            hintText.visibility = View.VISIBLE
            return
        }

        setBusy(true)
        manager.requestReissue(email) { result ->
            // requestReissue already dispatches to the main thread, so it's
            // safe to touch views here.
            setBusy(false)
            when (result) {
                RedemptionManager.ReissueResult.Sent -> {
                    onCompletion?.invoke(true)
                    dismissAllowingStateLoss()
                }
                RedemptionManager.ReissueResult.RateLimited -> {
                    hintText.text = "Too many attempts, try again later."
                    hintText.visibility = View.VISIBLE
                }
                RedemptionManager.ReissueResult.ServerError -> {
                    hintText.text = "Something went wrong, please try again."
                    hintText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        sendButton.isEnabled = !busy
        emailInput.isEnabled = !busy
    }

    private fun isValidEmail(value: String): Boolean {
        if (value.isBlank()) return false
        return Patterns.EMAIL_ADDRESS.matcher(value).matches()
    }

    private companion object {
        const val PADDING_DP = 16
        const val TITLE_TEXT_SIZE_SP = 14f
    }
}
