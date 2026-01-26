package io.qonversion.sample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.qonversion.android.sdk.dto.QRemoteConfig
import io.qonversion.sample.databinding.ItemRemoteConfigBinding
import org.json.JSONObject

class RemoteConfigsAdapter(
    private val configs: List<QRemoteConfig>
) : RecyclerView.Adapter<RemoteConfigsAdapter.ConfigViewHolder>() {

    class ConfigViewHolder(val binding: ItemRemoteConfigBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val binding = ItemRemoteConfigBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConfigViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        val config = configs[position]
        val context = holder.itemView.context

        with(holder.binding) {
            contextKey.text = config.source.contextKey ?: context.getString(R.string.empty_context_key)
            sourceName.text = config.source.name
            sourceType.text = config.source.type.name

            val payloadJson = config.payload
            payload.text = if (payloadJson != null) {
                try {
                    JSONObject(payloadJson.toString()).toString(2)
                } catch (e: Exception) {
                    payloadJson.toString()
                }
            } else {
                context.getString(R.string.no_payload)
            }

            val experiment = config.experiment
            if (experiment != null) {
                experimentInfo.text = context.getString(
                    R.string.experiment_info_format,
                    experiment.id,
                    experiment.name,
                    experiment.group.id,
                    experiment.group.name
                )
            } else {
                experimentInfo.text = context.getString(R.string.no_experiment)
            }
        }
    }

    override fun getItemCount() = configs.size
}
