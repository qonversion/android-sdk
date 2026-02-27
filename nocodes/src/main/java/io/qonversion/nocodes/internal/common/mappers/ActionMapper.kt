package io.qonversion.nocodes.internal.common.mappers

import io.qonversion.nocodes.dto.QAction

internal class ActionMapper : Mapper<QAction> {

    override fun fromMap(data: Map<*, *>): QAction {
        val typeString = data.getString("type")
        val type = QAction.Type.from(typeString)

        var parameters: MutableMap<QAction.Parameter, Any>? = null
        var rawParameters: MutableMap<String, Any>? = null

        if (data.containsKey("parameters")) {
            parameters = mutableMapOf()
            rawParameters = mutableMapOf()
            val parametersMap = data["parameters"] as? Map<*, *>
            parametersMap?.forEach { (key, value) ->
                if (key is String && value != null) {
                    rawParameters[key] = value  // Always preserve raw
                    QAction.Parameter.from(key)?.let { parameter ->
                        parameters[parameter] = value
                    }
                }
            }
        }

        return QAction(
            type,
            parameters,
            rawParameters,
        )
    }
}
