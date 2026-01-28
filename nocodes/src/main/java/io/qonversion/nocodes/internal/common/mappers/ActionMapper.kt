package io.qonversion.nocodes.internal.common.mappers

import io.qonversion.nocodes.dto.QAction

internal class ActionMapper : Mapper<QAction> {

    override fun fromMap(data: Map<*, *>): QAction {
        val typeString = data.getString("type")
        val type = QAction.Type.from(typeString)

        var parameters: MutableMap<QAction.Parameter, Any>? = null
        if (data.containsKey("parameters")) {
            parameters = mutableMapOf()
            val parametersMap = data["parameters"] as? Map<*, *>
            parametersMap?.forEach { (key, value) ->
                key.takeIf { it is String }
                    ?.let { QAction.Parameter.from(it as String) }
                    ?.let { parameter ->
                        value?.let { parameters[parameter] = value }
                    }
            }
        }

        return QAction(
            type,
            parameters,
        )
    }
}
