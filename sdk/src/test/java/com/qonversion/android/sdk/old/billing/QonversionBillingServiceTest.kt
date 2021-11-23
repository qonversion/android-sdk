package com.qonversion.android.sdk.old.billing

import android.app.Activity
import android.os.Handler
import com.android.billingclient.api.*
import com.qonversion.android.sdk.old.entity.PurchaseHistory
import com.qonversion.android.sdk.old.logger.Logger
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class QonversionBillingServiceTest {
    private val skuSubs = "subs"
    private val skuInapp = "inapp"
    private val purchaseToken = "token"

    private val mockBillingClient: BillingClient = mockk(relaxed = true)
    private val mockHandler: Handler = mockk()
    private val mockPurchasesListener: QonversionBillingService.PurchasesListener = mockk()
    private val mockLogger: Logger = mockk(relaxed = true)

    private lateinit var billingClientStateListener: BillingClientStateListener
    private lateinit var billingService: QonversionBillingService

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        val slot = slot<Runnable>()
        every {
            mockHandler.post(capture(slot))
        } answers {
            slot.captured.run()
            true
        }

        val billingClientStateSlot = slot<BillingClientStateListener>()
        every {
            mockBillingClient.startConnection(capture(billingClientStateSlot))
        } answers {
            billingClientStateListener = billingClientStateSlot.captured
        }

        every {
            mockBillingClient.isReady
        } returns true

        billingService =
            QonversionBillingService(mockHandler, mockPurchasesListener, mockLogger)
        billingService.billingClient = mockBillingClient
    }

    @Nested
    inner class QueryPurchasesHistory {
        @Test
        fun `query purchases history completed`() {
            mockQueryPurchaseHistoryResponse(
                BillingClient.SkuType.SUBS, BillingClient.BillingResponseCode.OK
            )
            mockQueryPurchaseHistoryResponse(
                BillingClient.SkuType.INAPP, BillingClient.BillingResponseCode.OK
            )

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
            assertThat(purchaseHistory!![0].historyRecord.sku).isEqualTo(skuSubs)
            assertThat(purchaseHistory!![0].type).isEqualTo(BillingClient.SkuType.SUBS)
            assertThat(purchaseHistory!![1].historyRecord.sku).isEqualTo(skuInapp)
            assertThat(purchaseHistory!![1].type).isEqualTo(BillingClient.SkuType.INAPP)
        }

        @Test
        fun `query purchases history failed with billing error`() {
            mockQueryPurchaseHistoryResponse(
                BillingClient.SkuType.SUBS,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
            )

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
        fun `query purchases history failed with null list`() {
            mockQueryPurchaseHistoryResponse(
                BillingClient.SkuType.SUBS,
                BillingClient.BillingResponseCode.OK,
                true
            )

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
                .isEqualTo(BillingClient.BillingResponseCode.OK)
        }

        @Test
        fun `query purchases history deferred until billing connected`() {
            mockQueryPurchaseHistoryResponse(
                BillingClient.SkuType.SUBS,
                BillingClient.BillingResponseCode.OK
            )
            mockQueryPurchaseHistoryResponse(
                BillingClient.SkuType.INAPP,
                BillingClient.BillingResponseCode.OK
            )

            every { mockBillingClient.isReady } returns false

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

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(purchaseHistory).isNotNull
        }

        @Test
        fun `query purchases history deferred until billing connected with error`() {
            every { mockBillingClient.isReady } returns false

            var billingError: BillingError? = null
            billingService.queryPurchasesHistory(
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                }
            )
            assertThat(billingError).isNull()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode).isEqualTo(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        }

        private fun mockQueryPurchaseHistoryResponse(
            @BillingClient.SkuType skuType: String,
            @BillingClient.BillingResponseCode responseCode: Int,
            isHistoryRecordListNull: Boolean = false
        ) {
            val purchaseHistoryResponse = slot<PurchaseHistoryResponseListener>()
            every {
                mockBillingClient.queryPurchaseHistoryAsync(
                    skuType,
                    capture(purchaseHistoryResponse)
                )
            } answers {
                var historyRecordList: List<PurchaseHistoryRecord>? = null

                if (responseCode == BillingClient.BillingResponseCode.OK && !isHistoryRecordListNull) {
                    val sku: String =
                        if (skuType == BillingClient.SkuType.INAPP) skuInapp else skuSubs
                    val historyRecord: PurchaseHistoryRecord = mockk(relaxed = true)
                    every { historyRecord.sku } returns sku

                    historyRecordList = listOf(historyRecord)
                }

                purchaseHistoryResponse.captured.onPurchaseHistoryResponse(
                    buildResult(responseCode),
                    historyRecordList
                )
            }
        }
    }

    @Nested
    inner class LoadProducts {
        private val sku = "sku"

        @Test
        fun `load products completed`() {
            mockSkuDetailsResponse(
                BillingClient.BillingResponseCode.OK
            )

            var skuDetailsList: List<SkuDetails>? = null
            billingService.loadProducts(
                setOf(sku),
                {
                    skuDetailsList = it
                },
                {
                    fail("Shouldn't go here")
                })

            assertThat(skuDetailsList).isNotNull
            assertThat(skuDetailsList!!.size).isEqualTo(2)
            assertThat(skuDetailsList!![0].sku).isEqualTo(skuSubs)
            assertThat(skuDetailsList!![0].type).isEqualTo(BillingClient.SkuType.SUBS)
            assertThat(skuDetailsList!![1].sku).isEqualTo(skuInapp)
            assertThat(skuDetailsList!![1].type).isEqualTo(BillingClient.SkuType.INAPP)
        }

        @Test
        fun `load products failed with billing error`() {
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
        fun `load products failed null list`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK, true)

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
                .isEqualTo(BillingClient.BillingResponseCode.OK)
        }

        @Test
        fun `load products deferred until billing connected`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)
            every { mockBillingClient.isReady } returns false

            var skuDetailsList: List<SkuDetails>? = null
            billingService.loadProducts(setOf(sku),
                {
                    skuDetailsList = it
                },
                {
                    fail("Shouldn't go here")
                })
            assertThat(skuDetailsList).isNull()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(skuDetailsList).isNotNull
        }

        @Test
        fun `load products deferred until billing connected with error`() {
            every { mockBillingClient.isReady } returns false

            var billingError: BillingError? = null
            billingService.loadProducts(setOf(sku),
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })
            assertThat(billingError).isNull()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode).isEqualTo(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        }
    }

    @Nested
    inner class Consume {
        private val purchaseToken = "token"

        @Test
        fun `consume completed`() {
            val consumeParams = slot<ConsumeParams>()
            mockConsumeResponse(consumeParams)

            billingService.consume(purchaseToken)
            assertThat(consumeParams.isCaptured).isTrue()
            assertThat(consumeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        @Test
        fun `consume deferred until billing connected`() {
            val consumeParams = slot<ConsumeParams>()
            mockConsumeResponse(consumeParams)

            every { mockBillingClient.isReady } returns false
            billingService.consume(purchaseToken)

            assertThat(consumeParams.isCaptured).isFalse()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(consumeParams.isCaptured).isTrue()
            assertThat(consumeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        private fun mockConsumeResponse(
            consumeParams: CapturingSlot<ConsumeParams>
        ) {
            val consumeResponse = slot<ConsumeResponseListener>()
            every {
                mockBillingClient.consumeAsync(
                    capture(consumeParams),
                    capture(consumeResponse)
                )
            } answers {
                consumeResponse.captured.onConsumeResponse(
                    buildResult(BillingClient.BillingResponseCode.OK), purchaseToken
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
            mockAcknowledgeResponse(acknowledgeParams)

            billingService.acknowledge(purchaseToken)
            assertThat(acknowledgeParams.isCaptured).isTrue()
            assertThat(acknowledgeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        @Test
        fun `acknowledge deferred until billing connected`() {
            val acknowledgeParams = slot<AcknowledgePurchaseParams>()
            mockAcknowledgeResponse(acknowledgeParams)

            every { mockBillingClient.isReady } returns false

            billingService.acknowledge(purchaseToken)
            assertThat(acknowledgeParams.isCaptured).isFalse()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(acknowledgeParams.isCaptured).isTrue()
            assertThat(acknowledgeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        private fun mockAcknowledgeResponse(
            acknowledgeParams: CapturingSlot<AcknowledgePurchaseParams>
        ) {
            val acknowledgeResponse = slot<AcknowledgePurchaseResponseListener>()
            every {
                mockBillingClient.acknowledgePurchase(
                    capture(acknowledgeParams),
                    capture(acknowledgeResponse)
                )
            } answers {
                acknowledgeResponse.captured.onAcknowledgePurchaseResponse(
                    buildResult(BillingClient.BillingResponseCode.OK)
                )
            }
        }
    }

    @Nested
    inner class QueryPurchases {
        @Test
        fun `query purchases completed`() {
            mockQueryPurchasesResponse(
                BillingClient.SkuType.SUBS,
                BillingClient.BillingResponseCode.OK
            )
            mockQueryPurchasesResponse(
                BillingClient.SkuType.INAPP,
                BillingClient.BillingResponseCode.OK
            )
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
            assertThat(purchases!![0].sku).isEqualTo(skuSubs)
            assertThat(purchases!![1].sku).isEqualTo(skuInapp)
        }

        @Test
        fun `query purchases completed with empty list`() {
            mockQueryPurchasesResponse(
                BillingClient.SkuType.SUBS,
                BillingClient.BillingResponseCode.OK, true
            )
            mockQueryPurchasesResponse(
                BillingClient.SkuType.INAPP,
                BillingClient.BillingResponseCode.OK, true
            )

            var purchases: List<com.android.billingclient.api.Purchase>? = null
            billingService.queryPurchases(
                {
                    purchases = it
                },
                {
                    fail("Shouldn't go here")
                })

            assertThat(purchases).isNotNull
            assertThat(purchases).isEmpty()
        }

        @Test
        fun `query purchases failed`() {
            mockQueryPurchasesResponse(
                BillingClient.SkuType.SUBS,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
            )
            mockQueryPurchasesResponse(
                BillingClient.SkuType.INAPP,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
            )

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
            mockQueryPurchasesResponse(
                BillingClient.SkuType.SUBS,
                BillingClient.BillingResponseCode.OK
            )
            mockQueryPurchasesResponse(
                BillingClient.SkuType.INAPP,
                BillingClient.BillingResponseCode.OK
            )
            every { mockBillingClient.isReady } returns false

            var purchases: List<com.android.billingclient.api.Purchase>? = null
            billingService.queryPurchases(
                {
                    purchases = it
                },
                {
                    fail("Shouldn't go here")
                })
            assertThat(purchases).isNull()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(purchases).isNotNull
        }

        @Test
        fun `query purchases deferred until billing connected with error`() {
            every { mockBillingClient.isReady } returns false

            var billingError: BillingError? = null
            billingService.queryPurchases(
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })
            assertThat(billingError).isNull()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode).isEqualTo(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        }

        private fun mockQueryPurchasesResponse(
            @BillingClient.SkuType skuType: String,
            @BillingClient.BillingResponseCode responseCode: Int,
            isPurchasesListNull: Boolean = false
        ) {
            var purchases: List<com.android.billingclient.api.Purchase>? = null

            if (responseCode == BillingClient.BillingResponseCode.OK && !isPurchasesListNull) {
                val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)
                val sku: String =
                    if (skuType == BillingClient.SkuType.INAPP) skuInapp else skuSubs
                every { purchase.sku } returns sku

                purchases = listOf(purchase)
            }
            every {
                mockBillingClient.queryPurchases(skuType)
            } returns com.android.billingclient.api.Purchase.PurchasesResult(
                buildResult(responseCode),
                purchases
            )
        }
    }

    @Nested
    inner class GetSkuDetailsFromPurchases {
        @Test
        fun `should return list with SUBS skuDetails`() {
            // given
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)

            var skuDetailsList: List<SkuDetails>? = null
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)
            every {
                purchase.sku
            } returns skuSubs

            // when
            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    skuDetailsList = it
                },
                {
                    fail("Shouldn't go here")
                })

            // then
            assertAll(
                "SkuDetails' list is not valid",
                { assertThat(skuDetailsList).isNotNull },
                { assertThat(skuDetailsList!!.size).isEqualTo(1) },
                { assertThat(skuDetailsList!![0].sku).isEqualTo(skuSubs) },
                { assertThat(skuDetailsList!![0].type).isEqualTo(BillingClient.SkuType.SUBS) }
            )
        }

        @Test
        fun `should failed with billing error`() {
            // given
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            var billingError: BillingError? = null
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)
            every {
                purchase.sku
            } returns skuSubs

            // when
            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })

            // then
            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode)
                .isEqualTo(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        }

        @Test
        fun `should failed with null list`() {
            // given
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK, true)

            var billingError: BillingError? = null
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)
            every {
                purchase.sku
            } returns skuSubs

            // when
            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })

            // then
            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode)
                .isEqualTo(BillingClient.BillingResponseCode.OK)
        }

        @Test
        fun `should wait for billing connected and then return skuDetails list`() {
            // given
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)
            every { mockBillingClient.isReady } returns false

            var skuDetailsList: List<SkuDetails>? = null
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)
            every {
                purchase.sku
            } returns skuSubs

            // when
            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    skuDetailsList = it
                },
                {
                    fail("Shouldn't go here")
                })

            // then
            assertThat(skuDetailsList).isNull()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(skuDetailsList).isNotNull
        }

        @Test
        fun `should wait for billing connected and then return error`() {
            // given
            every { mockBillingClient.isReady } returns false

            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)
            every {
                purchase.sku
            } returns skuSubs
            var billingError: BillingError? = null

            // when
            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })

            // then
            assertThat(billingError).isNull()

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode).isEqualTo(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        }
    }

    @Nested
    inner class Purchase {
        @Test
        fun `purchase billing flow params is correct`() {
            // given
            val sku = "monthly"
            val skuType = BillingClient.SkuType.SUBS
            val activity: Activity = mockk()

            mockkStatic(BillingFlowParams::class)

            val mockBuilder = mockk<BillingFlowParams.Builder>(relaxed = true)
            every {
                BillingFlowParams.newBuilder()
            } returns mockBuilder

            val skuDetailsSlot = slot<SkuDetails>()
            every {
                mockBuilder.setSkuDetails(capture(skuDetailsSlot))
            } returns mockBuilder

            val mockParams = mockk<BillingFlowParams>(relaxed = true)
            every {
                mockBuilder.build()
            } returns mockParams

            val mockSkuDetails = mockSkuDetails(sku, skuType)

            every {
                mockBillingClient.launchBillingFlow(eq(activity), mockParams)
            } answers {
                buildResult(BillingClient.BillingResponseCode.OK)
            }

            // when
            billingService.purchase(
                activity,
                mockSkuDetails
            )

            // then
            assertAll(
                "SkuDetails contains wrong fields",
                { assertEquals("Sku is incorrect", sku, skuDetailsSlot.captured.sku) },
                { assertEquals("SkuType is incorrect", skuType, skuDetailsSlot.captured.type) }
            )
        }

        @Test
        fun `purchase with oldSkuDetails billing flow params is correct`() {
            // given
            val oldSku = "weekly"
            val sku = "monthly"
            val skuType = BillingClient.SkuType.SUBS
            val activity: Activity = mockk()
            val prorationMode = BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE

            mockkStatic(BillingFlowParams::class)
            mockkStatic(BillingFlowParams.SubscriptionUpdateParams::class)

            val mockBuilder = mockk<BillingFlowParams.Builder>(relaxed = true)
            every {
                BillingFlowParams.newBuilder()
            } returns mockBuilder

            val skuDetailsSlot = slot<SkuDetails>()
            every {
                mockBuilder.setSkuDetails(capture(skuDetailsSlot))
            } returns mockBuilder

            val mockParams = mockk<BillingFlowParams>(relaxed = true)
            every {
                mockBuilder.build()
            } returns mockParams

            val mockSkuDetails = mockSkuDetails(sku, skuType)
            val mockOldSkuDetails = mockSkuDetails(oldSku, skuType)

            every {
                mockBillingClient.launchBillingFlow(eq(activity), mockParams)
            } answers {
                buildResult(BillingClient.BillingResponseCode.OK)
            }

            val mockSubscriptionUpdateParamsBuilder =
                mockk<BillingFlowParams.SubscriptionUpdateParams.Builder>(relaxed = true)
            every {
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
            } returns mockSubscriptionUpdateParamsBuilder

            val oldSkuPurchaseTokenSlot = slot<String>()
            every {
                mockSubscriptionUpdateParamsBuilder.setOldSkuPurchaseToken(
                    capture(
                        oldSkuPurchaseTokenSlot
                    )
                )
            } returns mockSubscriptionUpdateParamsBuilder

            val prorationModeSlot = slot<Int>()
            every {
                mockSubscriptionUpdateParamsBuilder.setReplaceSkusProrationMode(
                    capture(
                        prorationModeSlot
                    )
                )
            } returns mockSubscriptionUpdateParamsBuilder

            mockQueryPurchaseHistoryResponse(BillingClient.SkuType.SUBS, oldSku)

            // when
            billingService.purchase(
                activity,
                mockSkuDetails,
                mockOldSkuDetails,
                prorationMode
            )

            // then
            assertAll(
                "SkuDetails contains wrong fields",
                { assertEquals("Sku is incorrect", sku, skuDetailsSlot.captured.sku) },
                { assertEquals("SkuType is incorrect", skuType, skuDetailsSlot.captured.type) },
                {
                    assertEquals(
                        "ProrationMode is incorrect",
                        prorationMode,
                        prorationModeSlot.captured
                    )
                },
                {
                    assertEquals(
                        "PurchaseToken is incorrect",
                        purchaseToken,
                        oldSkuPurchaseTokenSlot.captured
                    )
                }
            )
            verify {
                mockBillingClient.launchBillingFlow(
                    activity,
                    mockParams
                )
            }
        }

        @Test
        fun `launch billing flow completed`() {
            val activity: Activity = mockk()
            val skuDetails: SkuDetails = mockk(relaxed = true)

            every {
                mockBillingClient.launchBillingFlow(any(), any())
            } returns buildResult(BillingClient.BillingResponseCode.OK)

            billingService.purchase(activity, skuDetails)

            verify {
                mockBillingClient.launchBillingFlow(
                    activity,
                    any()
                )
            }
        }

        @Test
        fun `launch billing flow failed`() {
            val activity: Activity = mockk()
            val skuDetails: SkuDetails = mockk(relaxed = true)

            every {
                mockBillingClient.launchBillingFlow(any(), any())
            } returns buildResult(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            billingService.purchase(activity, skuDetails)

            verify {
                mockBillingClient.launchBillingFlow(
                    activity,
                    any()
                ) wasNot Called
            }
        }

        @Test
        fun `launch billing flow deferred until billing connected`() {
            val activity: Activity = mockk()
            val skuDetails: SkuDetails = mockk(relaxed = true)

            every {
                mockBillingClient.launchBillingFlow(any(), any())
            } returns buildResult(BillingClient.BillingResponseCode.OK)
            every { mockBillingClient.isReady } returns false

            billingService.purchase(activity, skuDetails)
            verify {
                mockBillingClient.launchBillingFlow(eq(activity), any()) wasNot Called
            }

            every { mockBillingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            verify(exactly = 1) {
                mockBillingClient.launchBillingFlow(eq(activity), any())
            }
        }

        @Test
        fun `purchases listener completed`() {
            every {
                mockPurchasesListener.onPurchasesCompleted(any())
            } just Runs

            val purchase = mockk<com.android.billingclient.api.Purchase>()
            billingService.onPurchasesUpdated(
                buildResult(BillingClient.BillingResponseCode.OK),
                listOf(purchase)
            )

            verify {
                mockPurchasesListener.onPurchasesCompleted(
                    listOf(purchase)
                )
            }
        }

        @Test
        fun `purchases listener failed`() {
            every {
                mockPurchasesListener.onPurchasesFailed(any(), any())
            } just Runs

            billingService.onPurchasesUpdated(
                buildResult(BillingClient.BillingResponseCode.OK),
                null
            )

            verify {
                mockPurchasesListener.onPurchasesFailed(
                    emptyList(),
                    any()
                )
            }
        }

        private fun mockQueryPurchaseHistoryResponse(
            @BillingClient.SkuType skuType: String,
            sku: String? = null
        ) {
            val purchaseHistoryResponse = slot<PurchaseHistoryResponseListener>()
            every {
                mockBillingClient.queryPurchaseHistoryAsync(
                    skuType,
                    capture(purchaseHistoryResponse)
                )
            } answers {
                val historyRecord: PurchaseHistoryRecord = mockk(relaxed = true)
                if (sku != null) {
                    every { historyRecord.sku } returns sku
                }
                every { historyRecord.purchaseToken } returns purchaseToken

                val historyRecordList = listOf(historyRecord)

                purchaseHistoryResponse.captured.onPurchaseHistoryResponse(
                    buildResult(BillingClient.BillingResponseCode.OK),
                    historyRecordList
                )
            }
        }
    }

    @Test
    fun startConnection() {
        verify(exactly = 1) {
            mockHandler.post(any())
            mockBillingClient.startConnection(billingClientStateListener)
        }
    }

    private fun mockSkuDetailsResponse(
        @BillingClient.BillingResponseCode responseCode: Int,
        isSkuDetailsListNull: Boolean = false
    ) {
        val skuDetailsResponseSlot = slot<SkuDetailsResponseListener>()
        val skuDetailsParamsSlot = slot<SkuDetailsParams>()

        every {
            mockBillingClient.querySkuDetailsAsync(
                capture(skuDetailsParamsSlot),
                capture(skuDetailsResponseSlot)
            )
        } answers {
            val skuDetailsParams = skuDetailsParamsSlot.captured
            val skuType = skuDetailsParams.skuType
            var skuDetailsList: List<SkuDetails>? = null

            if (responseCode == BillingClient.BillingResponseCode.OK && !isSkuDetailsListNull) {
                val sku: String =
                    if (skuType == BillingClient.SkuType.INAPP) skuInapp else skuSubs
                val skuDetails: SkuDetails = mockk(relaxed = true)
                every { skuDetails.sku } returns sku
                every { skuDetails.type } returns skuType
                skuDetailsList = listOf(skuDetails)
            }

            skuDetailsResponseSlot.captured.onSkuDetailsResponse(
                buildResult(responseCode),
                skuDetailsList
            )
        }
    }

    private fun buildResult(@BillingClient.BillingResponseCode code: Int): BillingResult {
        return BillingResult.newBuilder().setResponseCode(code).build()
    }
}