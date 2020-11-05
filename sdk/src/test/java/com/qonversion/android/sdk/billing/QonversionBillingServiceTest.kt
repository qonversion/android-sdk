package com.qonversion.android.sdk.billing

import android.app.Activity
import android.os.Handler
import com.android.billingclient.api.*
import com.qonversion.android.sdk.entity.PurchaseHistory
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
    inner class QueryPurchasesHistory {
        @Test
        fun `query purchases history completed`() {
            mockQueryPurchaseHistoryResponse(BillingClient.BillingResponseCode.OK)

            var purchaseHistory: List<PurchaseHistory>? = null
            billingService.queryPurchasesHistory(
                {
                    purchaseHistory = it
                },
                {
                    fail("Shouldn't go here")
                }
            )
            assertThat(purchaseHistory).isNotNull
            assertThat(purchaseHistory!!.size).isEqualTo(2)

            verify(exactly = 1) {
                billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.SUBS, any())
            }
            verify(exactly = 1) {
                billingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, any())
            }
        }

        @Test
        fun `query purchases history failed`() {
            mockQueryPurchaseHistoryResponse(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            var billingError: BillingError? = null
            billingService.queryPurchasesHistory(
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                }
            )

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode)
                .isEqualTo(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        }

        @Test
        fun `query purchases history deferred until billing connected`() {
            mockQueryPurchaseHistoryResponse(BillingClient.BillingResponseCode.OK)
            every { billingClient.isReady } returns false

            var purchaseHistory: List<PurchaseHistory>? = null
            billingService.queryPurchasesHistory(
                {
                    purchaseHistory = it
                },
                {
                    fail("Shouldn't go here")
                }
            )
            assertThat(purchaseHistory).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(purchaseHistory).isNotNull
        }

        private fun mockQueryPurchaseHistoryResponse(@BillingClient.BillingResponseCode responseCode: Int) {
            val purchaseHistoryResponse = slot<PurchaseHistoryResponseListener>()
            every {
                billingClient.queryPurchaseHistoryAsync(
                    any(),
                    capture(purchaseHistoryResponse)
                )
            } answers {
                val historyRecordList: List<PurchaseHistoryRecord>? =
                    if (responseCode == BillingClient.BillingResponseCode.OK) listOf(mockk(relaxed = true))
                    else null

                purchaseHistoryResponse.captured.onPurchaseHistoryResponse(
                    buildResult(responseCode),
                    historyRecordList
                )
            }
        }
    }

    @Nested
    inner class LoadProducts {
        private val sku = "product_sku"

        @Test
        fun `load products completed`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)

            var skuDetailsList = listOf<SkuDetails>()
            billingService.loadProducts(
                setOf(sku),
                {
                    skuDetailsList = it
                },
                {
                    fail("Shouldn't go here")
                })

            assertThat(skuDetailsList).isNotEmpty
        }

        @Test
        fun `load products failed`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            var billingError: BillingError? = null
            billingService.loadProducts(
                setOf(sku),
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                }
            )

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode)
                .isEqualTo(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        }

        @Test
        fun `load products deferred until billing connected`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)
            every { billingClient.isReady } returns false

            var skuDetails: List<SkuDetails>? = null
            billingService.loadProducts(setOf(sku),
                {
                    skuDetails = it
                },
                {
                    fail("Shouldn't go here")
                })
            assertThat(skuDetails).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(skuDetails).isNotNull
        }
    }

    @Nested
    inner class Consume {
        private val purchaseToken = "token"

        @Test
        fun `consume completed`() {
            val consumeParams = slot<ConsumeParams>()
            mockConsumeResponse(BillingClient.BillingResponseCode.OK, consumeParams)

            billingService.consume(purchaseToken) {
                fail("Shouldn't go here")
            }

            assertThat(consumeParams.isCaptured).isTrue()
            assertThat(consumeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        @Test
        fun `consume failed`() {
            mockConsumeResponse(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            var billingError: BillingError? = null
            billingService.consume(purchaseToken) {
                billingError = it
            }

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode)
                .isEqualTo(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        }

        @Test
        fun `consume deferred until billing connected`() {
            val consumeParams = slot<ConsumeParams>()
            mockConsumeResponse(BillingClient.BillingResponseCode.OK, consumeParams)

            every { billingClient.isReady } returns false

            billingService.consume(purchaseToken) {
                fail("Shouldn't go here")
            }
            assertThat(consumeParams.isCaptured).isFalse()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(consumeParams.isCaptured).isTrue()
            assertThat(consumeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        private fun mockConsumeResponse(
            @BillingClient.BillingResponseCode responseCode: Int,
            consumeParams: CapturingSlot<ConsumeParams>? = null
        ) {
            val consumeResponse = slot<ConsumeResponseListener>()
            every {
                if (consumeParams != null) {
                    billingClient.consumeAsync(
                        capture(consumeParams),
                        capture(consumeResponse)
                    )
                } else {
                    billingClient.consumeAsync(any(), capture(consumeResponse))
                }
            } answers {
                consumeResponse.captured.onConsumeResponse(
                    buildResult(responseCode), purchaseToken
                )
            }
        }
    }

    @Nested
    inner class Acknowledge {
        private val purchaseToken = "token"

        @Test
        fun `acknowledge completed`() {
            val acknowledgeParams = slot<AcknowledgePurchaseParams>()
            mockAcknowledgeResponse(BillingClient.BillingResponseCode.OK, acknowledgeParams)

            billingService.acknowledge(purchaseToken) {
                fail("Shouldn't go here")
            }

            assertThat(acknowledgeParams.isCaptured).isTrue()
            assertThat(acknowledgeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        @Test
        fun `acknowledge failed`() {
            mockAcknowledgeResponse(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            var billingError: BillingError? = null
            billingService.acknowledge(purchaseToken) {
                billingError = it
            }

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode)
                .isEqualTo(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        }

        @Test
        fun `acknowledge deferred until billing connected`() {
            val acknowledgeParams = slot<AcknowledgePurchaseParams>()
            mockAcknowledgeResponse(BillingClient.BillingResponseCode.OK, acknowledgeParams)

            every { billingClient.isReady } returns false

            billingService.acknowledge(purchaseToken) {
                fail("Shouldn't go here")
            }
            assertThat(acknowledgeParams.isCaptured).isFalse()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(acknowledgeParams.isCaptured).isTrue()
            assertThat(acknowledgeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        private fun mockAcknowledgeResponse(
            @BillingClient.BillingResponseCode responseCode: Int,
            acknowledgeParams: CapturingSlot<AcknowledgePurchaseParams>? = null
        ) {
            val acknowledgeResponse = slot<AcknowledgePurchaseResponseListener>()
            every {
                if (acknowledgeParams != null) {
                    billingClient.acknowledgePurchase(
                        capture(acknowledgeParams),
                        capture(acknowledgeResponse)
                    )
                } else {
                    billingClient.acknowledgePurchase(any(), capture(acknowledgeResponse))
                }
            } answers {
                acknowledgeResponse.captured.onAcknowledgePurchaseResponse(
                    buildResult(responseCode)
                )
            }
        }
    }

    @Nested
    inner class QueryPurchases {
        @Test
        fun `query purchases completed`() {
            mockQueryPurchasesResponse(BillingClient.BillingResponseCode.OK)

            var purchases: List<com.android.billingclient.api.Purchase>? = null
            billingService.queryPurchases(
                {
                    purchases = it
                },
                {
                    fail("Shouldn't go here")
                })

            assertThat(purchases).isNotNull
            assertThat(purchases!!.size).isEqualTo(2)
        }

        @Test
        fun `query purchases failed`() {
            mockQueryPurchasesResponse(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            var billingError: BillingError? = null
            billingService.queryPurchases(
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode)
                .isEqualTo(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        }

        @Test
        fun `query purchases deferred until billing connected`() {
            mockQueryPurchasesResponse(BillingClient.BillingResponseCode.OK)
            every { billingClient.isReady } returns false

            var purchases: List<com.android.billingclient.api.Purchase>? = null
            billingService.queryPurchases(
                {
                    purchases = it
                },
                {
                    fail("Shouldn't go here")
                })
            assertThat(purchases).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(purchases).isNotNull
        }

        private fun mockQueryPurchasesResponse(
            @BillingClient.BillingResponseCode responseCode: Int
        ) {
            val purchases = listOf(mockk<com.android.billingclient.api.Purchase>(relaxed = true))

            every {
                billingClient.queryPurchases(any())
            } returns com.android.billingclient.api.Purchase.PurchasesResult(
                buildResult(responseCode),
                purchases
            )
        }
    }

    @Nested
    inner class GetSkuDetailsFromPurchases {
        @Test
        fun `get skuDetails from purchases completed`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)

            var skuDetailsList = listOf<SkuDetails>()
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)

            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    skuDetailsList = it
                },
                {
                    fail("Shouldn't go here")
                })

            assertThat(skuDetailsList.size).isEqualTo(1)
        }

        @Test
        fun `get skuDetails from purchases failed`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            var billingError: BillingError? = null
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)

            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode)
                .isEqualTo(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        }

        @Test
        fun `get skuDetails from purchases deferred until billing connected`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)
            every { billingClient.isReady } returns false

            var skuDetails: List<SkuDetails>? = null
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)

            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    skuDetails = it
                },
                {
                    fail("Shouldn't go here")
                })

            assertThat(skuDetails).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(skuDetails).isNotNull
        }
    }

    private fun mockSkuDetailsResponse(@BillingClient.BillingResponseCode responseCode: Int) {
        val skuDetailsResponse = slot<SkuDetailsResponseListener>()
        every {
            billingClient.querySkuDetailsAsync(
                any(),
                capture(skuDetailsResponse)
            )
        } answers {
            val skuList: List<SkuDetails>? =
                if (responseCode == BillingClient.BillingResponseCode.OK) listOf(mockk(relaxed = true))
                else null

            skuDetailsResponse.captured.onSkuDetailsResponse(
                buildResult(responseCode),
                skuList
            )
        }
    }

    private fun buildResult(@BillingClient.BillingResponseCode code: Int): BillingResult {
        return BillingResult.newBuilder().setResponseCode(code).build()
    }
}