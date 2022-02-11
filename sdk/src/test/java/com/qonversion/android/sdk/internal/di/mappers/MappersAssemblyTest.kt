package com.qonversion.android.sdk.internal.di.mappers

import com.qonversion.android.sdk.internal.common.mappers.EntitlementMapper
import com.qonversion.android.sdk.internal.common.mappers.UserMapper
import com.qonversion.android.sdk.internal.common.mappers.UserPurchaseMapper
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
            assertThat(result).isEqualToComparingFieldByField(expectedResult)
        }

        @Test
        fun `get different user mappers`() {
            // given

            // when
            val firstResult = mappersAssembly.userMapper()
            val secondResult = mappersAssembly.userMapper()

            // then
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }

    @Test
    fun `get different user purchase mappers`() {
        // given

        // when
        val firstResult = mappersAssembly.userPurchaseMapper()
        val secondResult = mappersAssembly.userPurchaseMapper()

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different entitlement mappers`() {
        // given

        // when
        val firstResult = mappersAssembly.entitlementMapper()
        val secondResult = mappersAssembly.entitlementMapper()

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different product mappers`() {
        // given

        // when
        val firstResult = mappersAssembly.productMapper()
        val secondResult = mappersAssembly.productMapper()

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different subscription mappers`() {
        // given

        // when
        val firstResult = mappersAssembly.subscriptionMapper()
        val secondResult = mappersAssembly.subscriptionMapper()

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different user properties mappers`() {
        // given

        // when
        val firstResult = mappersAssembly.userPropertiesMapper()
        val secondResult = mappersAssembly.userPropertiesMapper()

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
    }

    @Test
    fun `get different map data mappers`() {
        // given

        // when
        val firstResult = mappersAssembly.mapDataMapper()
        val secondResult = mappersAssembly.mapDataMapper()

        // then
        assertThat(firstResult).isNotEqualTo(secondResult)
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
            assertThat(firstResult).isNotEqualTo(secondResult)
        }
    }
}
