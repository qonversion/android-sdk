package com.qonversion.android.sdk.ad

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object ReflectionUtils {

    fun getMethod(className: String, methodName: String, vararg parameterTypes: Class<*>): Method? =
        try {
            val clazz = Class.forName(className)
            getMethod(clazz, methodName, *parameterTypes)
        } catch (ex: ClassNotFoundException) {
            null
        }

    fun getMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? =
        try {
            clazz.getMethod(methodName, *parameterTypes)
        } catch (ex: NoSuchMethodException) {
            null
        }

    fun invoke(receiver: Any?, method: Method, vararg args: Any?): Any? = try {
        method.invoke(receiver, *args)
    } catch (ex: IllegalAccessException) {
        null
    } catch (ex: InvocationTargetException) {
        null
    }
}