@file:OptIn(ExperimentalQonversionApi::class)

package io.qonversion.sample

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.ExperimentalQonversionApi
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigSource
import com.qonversion.android.sdk.dto.remoteconfig.QRemoteConfigValue
import io.qonversion.sample.databinding.ItemRemoteConfigV2Binding
import org.json.JSONArray
import org.json.JSONObject

/** One resolved Remote Config v2 key, paired with the context key it was read under. */
class ResolvedEntry(
    val contextKey: String,
    val value: QRemoteConfigValue<String>,
)

class RemoteConfigV2Adapter(
    private val entries: List<ResolvedEntry>
) : RecyclerView.Adapter<RemoteConfigV2Adapter.ValueViewHolder>() {

    class ValueViewHolder(val binding: ItemRemoteConfigV2Binding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ValueViewHolder {
        val binding = ItemRemoteConfigV2Binding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ValueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ValueViewHolder, position: Int) {
        val entry = entries[position]
        val context = holder.itemView.context

        with(holder.binding) {
            contextKey.text = entry.contextKey

            // The source is the whole point of v2: every key reports independently whether it came
            // from the server, the on-device cache, or the bundled fallback file.
            source.text = entry.value.source.name
            source.setTextColor(ContextCompat.getColor(context, entry.value.source.color()))

            applyPolicy.text = entry.value.applyPolicy.name
            variationUid.text = entry.value.variationUid.ifEmpty {
                context.getString(R.string.rc_v2_no_variation)
            }

            rawValue.text = prettyPrint(entry.value.value)

            val metadata = entry.value.metadataJson
            if (metadata == null) {
                metadataLabel.visibility = View.GONE
                metadataJson.visibility = View.GONE
            } else {
                metadataLabel.visibility = View.VISIBLE
                metadataJson.visibility = View.VISIBLE
                metadataJson.text = prettyPrint(metadata)
            }
        }
    }

    override fun getItemCount() = entries.size

    /** Values arrive as raw JSON text; indent them when they parse, show them verbatim otherwise. */
    private fun prettyPrint(raw: String): String = try {
        val trimmed = raw.trim()
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            else -> raw
        }
    } catch (e: Exception) {
        raw
    }

    private fun QRemoteConfigSource.color(): Int = when (this) {
        QRemoteConfigSource.Server -> R.color.colorGreen
        QRemoteConfigSource.Cache -> R.color.colorOrange
        QRemoteConfigSource.Fallback -> R.color.colorRed
    }
}
