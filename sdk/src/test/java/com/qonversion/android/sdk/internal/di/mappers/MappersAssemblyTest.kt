package com.qonversion.android.sdk.internal.di.mappers

import com.qonversion.android.sdk.internal.common.mappers.*
import com.qonversion.android.sdk.internal.common.mappers.error.ApiErrorMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MappersAssemblyTest {
    private lateinit var mappersAssembly: MappersAssembly

    @BeforeEach
    fun setUp() {
        mappersAssembly = MappersAssemblyImpl()
    }

    @Nested
    inner class UserMapperTest {
        private val mockUserPurchaseMapper = mockk<UserPurchaseMapper>()
        private val mockEntitlementMapper = mockk<EntitlementMapper>()

        @BeforeEach
        fun setup() {
            mappersAssembly = spyk(mappersAssembly)
            every {
                mappersAssembly.userPurchaseMapper()
            } returns mockUserPurchaseMapper

            every {
                mappersAssembly.entitlementMapper()
            } returns mockEntitlementMapper
        }

        @Test
        fun `get user mapper`() {
            // given
            val expectedResult = UserMapper(mockUserPurchaseMapper, mockEntitlementMapper)

            // when
            val result = mappersAssembly.userMapper()

            // then
            assertThat(result).isInstanceOf(UserMapper::class.java)
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.userMapper()
            val secondResult = mappersAssembly.userMapper()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class UserPurchaseMapperTest {
        @Test
        fun `get user purchase mapper`() {
            // given

            // when
            val result = mappersAssembly.userPurchaseMapper()

            // then
            assertThat(result).isInstanceOf(UserPurchaseMapper::class.java)
        }

        @Test
        fun `get different user purchase mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.userPurchaseMapper()
            val secondResult = mappersAssembly.userPurchaseMapper()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class EntitlementMapperTest {
        @Test
        fun `get entitlement mapper`() {
            // given

            // when
            val result = mappersAssembly.entitlementMapper()

            // then
            assertThat(result).isInstanceOf(EntitlementMapper::class.java)
        }

        @Test
        fun `get different entitlement mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.entitlementMapper()
            val secondResult = mappersAssembly.entitlementMapper()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class ProductMapperTest {
        @Test
        fun `get product mapper`() {
            // given

            // when
            val result = mappersAssembly.productMapper()

            // then
            assertThat(result).isInstanceOf(ProductMapper::class.java)
        }

        @Test
        fun `get different product mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.productMapper()
            val secondResult = mappersAssembly.productMapper()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class SubscriptionMapperTest {
        @Test
        fun `get subscription mapper`() {
            // given

            // when
            val result = mappersAssembly.subscriptionMapper()

            // then
            assertThat(result).isInstanceOf(SubscriptionMapper::class.java)
        }

        @Test
        fun `get different subscription mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.subscriptionMapper()
            val secondResult = mappersAssembly.subscriptionMapper()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class UserPropertiesMapperTest {
        @Test
        fun `get user properties mapper`() {
            // given

            // when
            val result = mappersAssembly.userPropertiesMapper()

            // then
            assertThat(result).isInstanceOf(UserPropertiesMapper::class.java)
        }

        @Test
        fun `get different user properties mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.userPropertiesMapper()
            val secondResult = mappersAssembly.userPropertiesMapper()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }


    @Nested
    inner class MapDataMapperTest {
        @Test
        fun `get map data mapper`() {
            // given

            // when
            val result = mappersAssembly.mapDataMapper()

            // then
            assertThat(result).isInstanceOf(MapDataMapper::class.java)
        }

        @Test
        fun `get different map data mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.mapDataMapper()
            val secondResult = mappersAssembly.mapDataMapper()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }

    @Nested
    inner class ApiErrorMapperTest {
        @Test
        fun `get api error mapper`() {
            // given

            // when
            val result = mappersAssembly.apiErrorMapper()

            // then
            assertThat(result).isInstanceOf(ApiErrorMapper::class.java)
        }

        @Test
        fun `get different api error mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.apiErrorMapper()
            val secondResult = mappersAssembly.apiErrorMapper()

            // then
            assertThat(firstResult).isNotSameAs(secondResult)
        }
    }
}
