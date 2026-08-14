package io.qonversion.nocodes.internal.networkLayer.networkClient

import io.qonversion.nocodes.internal.common.serializers.Serializer
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.Principal
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

internal class NetworkClientImplTest {
    @Test
    fun `https connections retain platform TLS verification`() {
        lateinit var platformConnection: TestHttpsURLConnection
        val url = URL(null, "https://api.qonversion.io", object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection {
                return TestHttpsURLConnection(url).also { platformConnection = it }
            }
        })
        val platformSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val platformHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

        val connection = NetworkClientImpl(UnusedSerializer()).connect(url) as HttpsURLConnection

        assertSame(platformConnection, connection)
        assertSame(platformSocketFactory, connection.sslSocketFactory)
        assertSame(platformHostnameVerifier, connection.hostnameVerifier)
    }

    private class UnusedSerializer : Serializer {
        override fun serialize(data: Map<String, Any?>): String = error("not used")
        override fun deserialize(payload: String): Any = error("not used")
    }

    private class TestHttpsURLConnection(url: URL) : HttpsURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getCipherSuite(): String = ""
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerPrincipal(): Principal? = null
        override fun getLocalPrincipal(): Principal? = null
    }
}
