package com.qonversion.android.sdk

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.qonversion.android.sdk.billing.milliSecondsToSeconds
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test


class UtilsTest {
    private val packageName = "packageName"
    private val installDate: Long = 1605608753

    private val mockManager = mockk<PackageManager>()
    private val mockContext = mockk<Application>(relaxed = true)

    private lateinit var utils: Utils

    @Before
    fun setUp() {
        clearAllMocks()

        mockInstallDate()
        utils = Utils(mockContext)
    }

    @Test
    fun `get install date when field is not set`() {
        val testInstallDate = utils.getInstallDate()
        assertThat(testInstallDate).isEqualTo(installDate.milliSecondsToSeconds())
    }

    @Test
    fun `get install date when field is set`() {
        var testInstallDate = utils.getInstallDate()
        testInstallDate = utils.getInstallDate()
        verify(exactly = 1) {
            mockManager.getPackageInfo(
                packageName,
                0
            )
        }

        assertThat(testInstallDate).isEqualTo(installDate.milliSecondsToSeconds())
    }

    private fun mockInstallDate(){
        val info = mockk<PackageInfo>()

        every {
            mockContext.packageName
        } returns packageName

        every {
            mockContext.packageManager
        } returns mockManager

        info.firstInstallTime = installDate

        every {
            mockManager.getPackageInfo(packageName, 0)
        } returns info
    }
}