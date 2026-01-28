package io.qonversion.nocodes.internal.common.mappers

import io.qonversion.nocodes.dto.QAction
import io.qonversion.nocodes.dto.QSuccessFailureAction
import io.qonversion.nocodes.dto.QSuccessFailureActionType

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

        // Parse success/failure actions for purchase and restore actions
        var successAction: QSuccessFailureAction? = null
        var failureAction: QSuccessFailureAction? = null

        if (type == QAction.Type.Purchase || type == QAction.Type.Restore) {
            successAction = parseSuccessFailureAction(data, "successAction", "successActionValue")
            failureAction = parseSuccessFailureAction(data, "failureAction", "failureActionValue")
        }

        return QAction(
            type,
            parameters,
            successAction,
            failureAction
        )
    }

    private fun parseSuccessFailureAction(
        data: Map<*, *>,
        typeKey: String,
        valueKey: String
    ): QSuccessFailureAction? {
        val actionTypeString = data.getString(typeKey) ?: return null
        val actionType = QSuccessFailureActionType.from(actionTypeString) ?: return null
        val value = data.getString(valueKey)
        return QSuccessFailureAction(actionType, value)
    }
}
