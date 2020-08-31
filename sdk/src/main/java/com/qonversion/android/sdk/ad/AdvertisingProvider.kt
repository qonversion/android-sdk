package com.qonversion.android.sdk.ad

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import java.util.concurrent.LinkedBlockingQueue

class AdvertisingProvider {

    interface Callback {
        fun onSuccess(advertisingId: String)
        fun onFailure(t: Throwable)
    }

    fun init(context: Context, callback: Callback) {
        // Solution with reflection isn't working for some reasons:
        // 1) it requires dependency on com.google.android.gms:play-services-ads to find
        // com.google.android.gms.ads.identifier.AdvertisingIdClient class which requires additional
        // initialization in its' turn,
        // 2) reflections still can't call getAdvertisingIdInfo method of that class with null
        // receiver even though it is static,
        // 3) isGooglePlayServicesAvailable check also requires additional dependencies, but can be
        // omitted, because getAdvertisingIdInfo should check GP services for itself.

        try {
            getAdvertisingIdViaReflection(context)?.let {
                callback.onSuccess(it)
            }
        } catch (e: Exception) {
            getAdvertisingIdViaService(context, callback)
        }
    }

    @Throws(Exception::class)
    fun getAdvertisingIdViaReflection(context: Context): String? {
        if (!isGooglePlayServicesAvailable(context)) {
            return null
        }

        val getAdvertisingIdInfo = ReflectionUtils.getMethod(
            "com.google.android.gms.ads.identifier.AdvertisingIdClient",
            "getAdvertisingIdInfo",
            Context::class.java
        ) ?: throw IllegalStateException("AdvertisingIdClient not found")

        val advertisingInfo =
            ReflectionUtils.invoke(null, getAdvertisingIdInfo, context)
                ?: throw IllegalStateException("getAdvertisingIdInfo invocation failed")
        val getId = ReflectionUtils.getMethod(advertisingInfo.javaClass, "getId")
            ?: throw IllegalStateException("getId invocation failed")

        return ReflectionUtils.invoke(advertisingInfo, getId) as String
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val method =
            ReflectionUtils.getMethod(
                "com.google.android.gms.common.GooglePlayServicesUtil",
                "isGooglePlayServicesAvailable",
                Context::class.java
            ) ?: return false

        val connectionResult = ReflectionUtils.invoke(null, method, context)
        return connectionResult is Int && connectionResult == 0
    }

    @Throws(IllegalStateException::class)
    fun getAdvertisingIdViaService(context: Context, callback: Callback) {
        Thread(Runnable {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw IllegalStateException("Cannot be called from the main thread")
            }

            val connection = AdvertisingConnection()
            val intent = Intent("com.google.android.gms.ads.identifier.service.START").apply {
                setPackage("com.google.android.gms")
            }
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                callback.onFailure(IllegalStateException("Binding to advertising id service failed"))
            }

            try {
                AdvertisingInterface(connection.binder).id?.let { id ->
                    callback.onSuccess(id)
                }
            } catch (e: Exception) {
                callback.onFailure(e)
            } finally {
                context.unbindService(connection)
            }
        }).start()
    }

    class AdvertisingConnection : ServiceConnection {
        private var retrieved = false
        private val queue = LinkedBlockingQueue<IBinder>(1)

        internal val binder: IBinder
            @Throws(InterruptedException::class)
            get() {
                if (this.retrieved) throw IllegalStateException()
                this.retrieved = true
                return this.queue.take() as IBinder
            }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            try {
                this.queue.put(service)
            } catch (localInterruptedException: InterruptedException) {
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }

    class AdvertisingInterface(private val binder: IBinder) : IInterface {

        val id: String?
            @Throws(RemoteException::class)
            get() {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                val id: String?
                try {
                    data.writeInterfaceToken("com.google.android.gms.ads.identifier.internal.IAdvertisingIdService")
                    binder.transact(1, data, reply, 0)
                    reply.readException()
                    id = reply.readString()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
                return id
            }

        override fun asBinder(): IBinder {
            return binder
        }
    }
}