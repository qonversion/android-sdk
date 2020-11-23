package com.qonversion.android.sdk

import android.app.Application
import android.os.Build
import com.qonversion.android.sdk.billing.QonversionBillingService
import com.qonversion.android.sdk.logger.Logger
import io.mockk.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class QProductCenterManagerTest {
    private val mockLogger: Logger = mockk(relaxed = true)
    private val mockContext = mockk<Application>(relaxed = true)
    private val mockRepository = mockk<QonversionRepository>(relaxed = true)
    private val mockBillingService: QonversionBillingService = mockk()
    private val mockConsumer = mockk<Consumer>(relaxed = true)

    private lateinit var productCenterManager: QProductCenterManager

    private val fieldIsLaunchingFinished = "isLaunchingFinished"

    @Before
    fun setUp() {
        clearAllMocks()

        productCenterManager =
            QProductCenterManager(mockContext, mockRepository, mockLogger)
        productCenterManager.billingService = mockBillingService
        productCenterManager.consumer = mockConsumer
        productCenterManager.mockPrivateField(fieldIsLaunchingFinished, true)
    }

    private fun Any.mockPrivateField(fieldName: String, field: Any) {
        javaClass.declaredFields
            .filter { it.modifiers.and(Modifier.PRIVATE) > 0 || it.modifiers.and(Modifier.PROTECTED) > 0 }
            .firstOrNull { it.name == fieldName }
            ?.also { it.isAccessible = true }
            ?.set(this, field)
    }
}