package com.qonversion.android.sdk.billing

import android.app.Activity
import android.app.Application
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
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class QonversionBillingServiceTest {
    private val skuSubs = "subs"
    private val skuInapp = "inapp"

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
        private val fieldName = "billingClient"

        @Test
        fun `init completed`() {
            verify(exactly = 1) {
                billingBuilder.build(purchasesUpdatedListener)
                billingClient.startConnection(billingClientStateListener)
            }

            val memberProperty =
                QonversionBillingService::class.memberProperties.find { it.name == fieldName }

            memberProperty?.let {
                it.isAccessible = true
                val fieldBillingClient = it.get(billingService) as BillingClient?
                assertThat(fieldBillingClient).isEqualTo(billingClient)
            }
        }
    }

    @Nested
    inner class BillingBuilder {
        @Test
        fun build() {
            val context = mockk<Application>()
            mockkStatic(BillingClient::class)
            val mockBuilder = mockk<BillingClient.Builder>(relaxed = true)
            every {
                BillingClient.newBuilder(context)
            } returns mockBuilder

            QonversionBillingService.BillingBuilder(context).build(purchasesUpdatedListener)
            verify(exactly = 1) {
                mockBuilder.enablePendingPurchases()
                mockBuilder.setListener(purchasesUpdatedListener)
            }
        }
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

        @Test
        fun `query purchases history deferred until billing connected with error`() {
            every { billingClient.isReady } returns false

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

            every { billingClient.isReady } returns true
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
                billingClient.queryPurchaseHistoryAsync(
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
            every { billingClient.isReady } returns false

            var skuDetailsList: List<SkuDetails>? = null
            billingService.loadProducts(setOf(sku),
                {
                    skuDetailsList = it
                },
                {
                    fail("Shouldn't go here")
                })
            assertThat(skuDetailsList).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(skuDetailsList).isNotNull
        }

        @Test
        fun `load products deferred until billing connected with error`() {
            every { billingClient.isReady } returns false

            var billingError: BillingError? = null
            billingService.loadProducts(setOf(sku),
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })
            assertThat(billingError).isNull()

            every { billingClient.isReady } returns true
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

            every { billingClient.isReady } returns false
            billingService.consume(purchaseToken)

            assertThat(consumeParams.isCaptured).isFalse()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(consumeParams.isCaptured).isTrue()
            assertThat(consumeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        private fun mockConsumeResponse(
            consumeParams: CapturingSlot<ConsumeParams>
        ) {
            val consumeResponse = slot<ConsumeResponseListener>()
            every {
                billingClient.consumeAsync(
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

            every { billingClient.isReady } returns false

            billingService.acknowledge(purchaseToken)
            assertThat(acknowledgeParams.isCaptured).isFalse()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(acknowledgeParams.isCaptured).isTrue()
            assertThat(acknowledgeParams.captured.purchaseToken).isEqualTo(purchaseToken)
        }

        private fun mockAcknowledgeResponse(
            acknowledgeParams: CapturingSlot<AcknowledgePurchaseParams>
        ) {
            val acknowledgeResponse = slot<AcknowledgePurchaseResponseListener>()
            every {
                billingClient.acknowledgePurchase(
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

        @Test
        fun `query purchases deferred until billing connected with error`() {
            every { billingClient.isReady } returns false

            var billingError: BillingError? = null
            billingService.queryPurchases(
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it
                })
            assertThat(billingError).isNull()

            every { billingClient.isReady } returns true
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
                billingClient.queryPurchases(skuType)
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

            var skuDetailsList: List<SkuDetails>? = null
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)

            billingService.getSkuDetailsFromPurchases(listOf(purchase),
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
        fun `get skuDetails from purchases failed with billing error`() {
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
        fun `get skuDetails from purchases failed with null list`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK, true)

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
                .isEqualTo(BillingClient.BillingResponseCode.OK)
        }

        @Test
        fun `get skuDetails from purchases deferred until billing connected`() {
            mockSkuDetailsResponse(BillingClient.BillingResponseCode.OK)
            every { billingClient.isReady } returns false

            var skuDetailsList: List<SkuDetails>? = null
            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)

            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    skuDetailsList = it
                },
                {
                    fail("Shouldn't go here")
                })

            assertThat(skuDetailsList).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            assertThat(skuDetailsList).isNotNull
        }

        @Test
        fun `get skuDetails from purchases deferred until billing connected with error`() {
            every { billingClient.isReady } returns false

            val purchase = mockk<com.android.billingclient.api.Purchase>(relaxed = true)

            var billingError: BillingError? = null
            billingService.getSkuDetailsFromPurchases(listOf(purchase),
                {
                    fail("Shouldn't go here")
                },
                {
                    billingError = it

                })

            assertThat(billingError).isNull()

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))

            assertThat(billingError).isNotNull
            assertThat(billingError!!.billingResponseCode).isEqualTo(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
        }
    }

    @Nested
    inner class Purchase {
        @Test
        fun `purchase billing flow params is correct`() {
            val activity: Activity = mockk()
            val skuType = BillingClient.SkuType.SUBS

            val newSku = "monthly"
            val newSkuDetails = mockk<SkuDetails>().also {
                every { it.sku } returns newSku
                every { it.type } returns skuType
            }

            val slot = slot<BillingFlowParams>()
            var billingParams: BillingFlowParams? = null
            every {
                billingClient.launchBillingFlow(eq(activity), capture(slot))
            } answers {
                billingParams = slot.captured
                buildResult(BillingClient.BillingResponseCode.OK)
            }

            billingService.purchase(
                activity,
                newSkuDetails
            )

            assertThat(billingParams).isNotNull
            assertThat(billingParams!!.sku).isEqualTo(newSku)
            assertThat(billingParams!!.skuType).isEqualTo(skuType)
        }

        @Test
        fun `purchase with oldSkuDetails billing flow params is correct`() {
            val activity: Activity = mockk()
            val skuType = BillingClient.SkuType.SUBS
            val prorationMode = BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE

            val oldSku = "weekly"
            val newSku = "monthly"
            val newSkuDetails = mockk<SkuDetails>().also {
                every { it.sku } returns newSku
                every { it.type } returns skuType
            }
            val oldSkuDetails = mockk<SkuDetails>().also {
                every { it.sku } returns oldSku
                every { it.type } returns skuType
            }
            mockQueryPurchaseHistoryResponse(BillingClient.SkuType.SUBS, oldSku)

            val slot = slot<BillingFlowParams>()
            var billingParams: BillingFlowParams? = null
            every {
                billingClient.launchBillingFlow(eq(activity), capture(slot))
            } answers {
                billingParams = slot.captured

                buildResult(BillingClient.BillingResponseCode.OK)
            }

            billingService.purchase(
                activity,
                newSkuDetails,
                oldSkuDetails,
                prorationMode
            )

            assertThat(billingParams).isNotNull
            assertThat(billingParams!!.sku).isEqualTo(newSku)
            assertThat(billingParams!!.skuType).isEqualTo(skuType)
            assertThat(billingParams!!.oldSku).isEqualTo(oldSku)
            assertThat(billingParams!!.replaceSkusProrationMode).isEqualTo(prorationMode)
        }

        @Test
        fun `replaceOldPurchase when it exists`() {
            val activity: Activity = mockk()
            val skuType = BillingClient.SkuType.SUBS
            val prorationMode = BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE

            val newSkuDetails = mockk<SkuDetails>(relaxed = true).also {
                every { it.type } returns skuType
            }
            val oldSkuDetails = mockk<SkuDetails>(relaxed = true).also {
                every { it.type } returns skuType
            }
            mockQueryPurchaseHistoryResponse(BillingClient.SkuType.SUBS)

            every {
                billingClient.launchBillingFlow(any(), any())
            } returns buildResult(BillingClient.BillingResponseCode.OK)

            billingService.purchase(
                activity,
                newSkuDetails,
                oldSkuDetails,
                prorationMode
            )

            verify {
                billingClient.launchBillingFlow(
                    activity,
                    any()
                )
            }
        }

        @Test
        fun `launch billing flow completed`() {
            val activity: Activity = mockk()
            val skuDetails: SkuDetails = mockk(relaxed = true)

            every {
                billingClient.launchBillingFlow(any(), any())
            } returns buildResult(BillingClient.BillingResponseCode.OK)

            billingService.purchase(activity, skuDetails)

            verify {
                billingClient.launchBillingFlow(
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
                billingClient.launchBillingFlow(any(), any())
            } returns buildResult(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)

            billingService.purchase(activity, skuDetails)

            verify {
                billingClient.launchBillingFlow(
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
                billingClient.launchBillingFlow(any(), any())
            } returns buildResult(BillingClient.BillingResponseCode.OK)
            every { billingClient.isReady } returns false

            billingService.purchase(activity, skuDetails)
            verify {
                billingClient.launchBillingFlow(eq(activity), any()) wasNot Called
            }

            every { billingClient.isReady } returns true
            billingClientStateListener.onBillingSetupFinished(buildResult(BillingClient.BillingResponseCode.OK))

            verify(exactly = 1) {
                billingClient.launchBillingFlow(eq(activity), any())
            }
        }

        @Test
        fun `purchases listener completed`() {
            every {
                purchasesListener.onPurchasesCompleted(any())
            } just Runs

            val purchase = mockk<com.android.billingclient.api.Purchase>()
            purchasesUpdatedListener.onPurchasesUpdated(
                buildResult(BillingClient.BillingResponseCode.OK),
                listOf(purchase)
            )

            verify {
                purchasesListener.onPurchasesCompleted(
                    listOf(purchase)
                )
            }
        }

        @Test
        fun `purchases listener failed`() {
            every {
                purchasesListener.onPurchasesFailed(any(), any())
            } just Runs

            purchasesUpdatedListener.onPurchasesUpdated(
                buildResult(BillingClient.BillingResponseCode.OK),
                null
            )

            verify {
                purchasesListener.onPurchasesFailed(
                    emptyList(),
                    any()
                )
            }
        }

        private fun mockQueryPurchaseHistoryResponse(
            @BillingClient.SkuType skuType: String,
            sku: String ? = null
        ) {
            val purchaseHistoryResponse = slot<PurchaseHistoryResponseListener>()
            every {
                billingClient.queryPurchaseHistoryAsync(
                    skuType,
                    capture(purchaseHistoryResponse)
                )
            } answers {
                val historyRecord: PurchaseHistoryRecord = mockk(relaxed = true)
                if(sku!=null){
                    every { historyRecord.sku } returns sku
                }

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
            handler.post(any())
            billingClient.startConnection(billingClientStateListener)
        }
    }

    private fun mockSkuDetailsResponse(
        @BillingClient.BillingResponseCode responseCode: Int,
        isSkuDetailsListNull: Boolean = false
    ) {
        val skuDetailsResponseSlot = slot<SkuDetailsResponseListener>()
        val skuDetailsParamsSlot = slot<SkuDetailsParams>()

        every {
            billingClient.querySkuDetailsAsync(
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