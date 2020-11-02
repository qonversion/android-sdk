package com.qonversion.android.sdk.billing

import android.os.Handler
import com.android.billingclient.api.*
import com.qonversion.android.sdk.logger.Logger
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class QonversionBillingServiceTest {
    private val billingBuilder: QonversionBillingService.BillingBuilder = mockk()
    private val billingClient: BillingClient = mockk()
    private val handler: Handler = mockk()
    private val purchasesListener: QonversionBillingService.PurchasesListener = mockk()
    private val logger: Logger = mockk(relaxed = true)

    private lateinit var purchasesUpdatedListener: PurchasesUpdatedListener
    private lateinit var billingClientStateListener: BillingClientStateListener
    private lateinit var billingService: QonversionBillingService

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        val slot = slot<Runnable>()
        every {
            handler.post(capture(slot))
        } answers {
            slot.captured.run()
            true
        }

        val purchasesUpdatedSlot = slot<PurchasesUpdatedListener>()
        every {
            billingBuilder.build(capture(purchasesUpdatedSlot))
        } answers {
            purchasesUpdatedListener = purchasesUpdatedSlot.captured
            billingClient
        }

        val billingClientStateSlot = slot<BillingClientStateListener>()
        every {
            billingClient.startConnection(capture(billingClientStateSlot))
        } answers {
            billingClientStateListener = billingClientStateSlot.captured
        }

        every {
            billingClient.isReady
        } returns true

        billingService =
            QonversionBillingService(billingBuilder, handler, purchasesListener, logger)
    }

    @Nested
    inner class Init {
        @Test
        fun build() {
            verify(exactly = 1) {
                billingBuilder.build(purchasesUpdatedListener)
            }
        }

        @Test
        fun startConnection() {
            verify(exactly = 1) {
                billingClient.startConnection(billingClientStateListener)
            }
        }
    }

    @Nested
    inner class LoadProducts {
        private val productSku = "test_purchase"

        @Test
        fun `load products completed`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)

            billingService.loadProducts(setOf(productSku),
                {
                    assertThat(it).isNotEmpty
                },
                {
                    fail("Shouldn't go here")
                })
        }

        @Test
        fun `load products failed`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)

            billingService.loadProducts(setOf(productSku),
                {
                    fail("Shouldn't go here")
                },
                {
                    assertThat(it.billingResponseCode)
                        .isEqualTo(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
                })

        }

        @Test
        fun `load products deferred until connected`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)

            every { billingClient.isReady } returns false

            var skuDetails: List<SkuDetails>? = null
            billingService.loadProducts(setOf(productSku),
                {
                    skuDetails = it
                },
                {
                    fail("Shouldn't go here onerror")
                })

            assertThat(skuDetails).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(skuDetails).isNotNull
        }

        private fun mockSkuDetailsResponse(@BillingClient.BillingResponseCode responseCode: Int) {
            val skuDetailsResponse = slot<SkuDetailsResponseListener>()
            every {
                billingClient.querySkuDetailsAsync(
                    any(),
                    capture(skuDetailsResponse)
                )
            } answers {
                if (responseCode == BillingClient.BillingResponseCode.OK) {
                    val skuDetails: SkuDetails = mockk(relaxed = true)
                    skuDetailsResponse.captured.onSkuDetailsResponse(
                        buildResult(BillingClient.BillingResponseCode.OK),
                        listOf(skuDetails)
                    )
                } else {
                    skuDetailsResponse.captured.onSkuDetailsResponse(
                        buildResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE),
                        null
                    )
                }
            }
        }
    }

    @Nested
    inner class QueryPurchasesHistory {
        @Test
        fun `query purchases history completed`() {
            mockPurchaseHistoryResponse(BillingClient.BillingResponseCode.OK)

            billingService.queryPurchasesHistory(
                {
                    assertThat(it.size).isEqualTo(2)
                },
                {
                    fail("Shouldn't go here")
                }
            )

            verify(exactly = 1) {
                billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, any())
            }
            verify(exactly = 1) {
                billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, any())
            }
        }

        @Test
        fun `query purchases history failed`() {
            mockPurchaseHistoryResponse(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)

            billingService.queryPurchasesHistory(
                {
                    fail("Shouldn't go here")
                },
                {
                    assertThat(it.billingResponseCode)
                        .isEqualTo(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
                }
            )
        }

        @Test
        fun `query purchases deferred until connected`() {
            mockPurchaseHistoryResponse(BillingClient.BillingResponseCode.OK)

            every { billingClient.isReady } returns false

            var historyRecord: List<PurchaseHistoryRecord>? = null
            billingService.queryPurchasesHistory(
                {
                    historyRecord = it
                },
                {
                    fail("Shouldn't go here")
                }
            )
            assertThat(historyRecord).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(historyRecord).isNotNull
        }

        private fun mockPurchaseHistoryResponse(@BillingClient.BillingResponseCode responseCode: Int) {
            val purchaseHistoryResponse = slot<PurchaseHistoryResponseListener>()
            every {
                billingClient.queryPurchaseHistoryAsync(
                    any(),
                    capture(purchaseHistoryResponse)
                )
            } answers {
                if (responseCode == BillingClient.BillingResponseCode.OK) {
                    val historyRecord: PurchaseHistoryRecord = mockk(relaxed = true)
                    purchaseHistoryResponse.captured.onPurchaseHistoryResponse(
                        buildResult(BillingClient.BillingResponseCode.OK),
                        listOf(historyRecord)
                    )
                } else {
                    purchaseHistoryResponse.captured.onPurchaseHistoryResponse(
                        buildResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE),
                        null
                    )
                }
            }
        }
    }

    private fun buildResult(@BillingClient.BillingResponseCode code: Int): BillingResult {
        return BillingResult.newBuilder().setResponseCode(code).build()
    }
}