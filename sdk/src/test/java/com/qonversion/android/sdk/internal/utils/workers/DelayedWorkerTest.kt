package com.qonversion.android.sdk.internal.utils.workers

import io.mockk.called
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException

internal class DelayedWorkerTest {

    private lateinit var worker: DelayedWorkerImpl

    @BeforeEach
    fun setUp() {
        worker = DelayedWorkerImpl()
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class DoDelayedTest {

        @Test
        fun `do normal delayed job`() = runTest {
            // given
            worker = spyk(DelayedWorkerImpl(this))
            var isActionCalled = false
            val delay = 5000L
            every { worker.isInProgress() } returns false

            // when
            worker.doDelayed(delay) {
                isActionCalled = true
            }

            // then
            verify(exactly = 1) { worker.isInProgress() }
            assertThat(worker.job).isNotNull
            assertThat(isActionCalled).isFalse
            advanceTimeBy(delay / 2)
            assertThat(isActionCalled).isFalse
            advanceTimeBy(delay / 2 + 1)
            assertThat(isActionCalled).isTrue
            assertThat(worker.job?.isCompleted).isTrue
        }

        @Test
        fun `do delayed job when another one is in progress`() {
            // given
            val coroutineScope = mockk<CoroutineScope>()
            worker = spyk(DelayedWorkerImpl(coroutineScope))
            every { worker.isInProgress() } returns true

            // when
            worker.doDelayed(1234L) {
                throw IllegalStateException("This code should not be reached")
            }

            // then
            verify(exactly = 1) { worker.isInProgress() }
            verify { coroutineScope wasNot called }
        }

        @Test
        fun `do delayed job ignoring existing one`() = runTest {
            // given
            worker = spyk(DelayedWorkerImpl(this))
            var isActionCalled = false
            val delay = 5000L
            every { worker.isInProgress() } returns true

            // when
            worker.doDelayed(delay, ignoreExistingJob = true) {
                isActionCalled = true
            }

            // then
            verify(exactly = 0) { worker.isInProgress() }
            assertThat(worker.job).isNotNull
            assertThat(isActionCalled).isFalse
            advanceTimeBy(delay / 2)
            assertThat(isActionCalled).isFalse
            advanceTimeBy(delay / 2 + 1)
            assertThat(isActionCalled).isTrue
            assertThat(worker.job?.isCompleted).isTrue
        }

        @Test
        fun `check that job is exactly the launched one`() = runTest {
            // given
            worker = spyk(DelayedWorkerImpl(this))
            var isActionCalled = false
            val delay = 5000L
            every { worker.isInProgress() } returns false

            // when
            worker.doDelayed(delay) {
                isActionCalled = true
            }

            // then
            verify(exactly = 1) { worker.isInProgress() }
            assertThat(worker.job).isNotNull
            worker.job?.cancel()
            advanceTimeBy(delay + 1)
            assertThat(isActionCalled).isFalse
        }
    }

    @ExperimentalCoroutinesApi
    @Nested
    inner class DoImmediatelyTest {

        @Test
        fun `do immediately`() = runTest {
            // given
            worker = spyk(DelayedWorkerImpl(this))
            var isActionCalled = false
            every { worker.cancel() } just runs
            worker.job = null

            // when
            worker.doImmediately {
                isActionCalled = true
            }

            // then
            verify(exactly = 1) { worker.cancel() }
            assertThat(worker.job).isNotNull
            assertThat(isActionCalled).isFalse
            advanceTimeBy(1)
            assertThat(isActionCalled).isTrue
            assertThat(worker.job?.isCompleted).isTrue
            verify(exactly = 0) { worker.isInProgress() }
        }
    }

    @Nested
    inner class CancelTest {

        @Test
        fun `cancelling started job`() {
            // given
            val job = mockk<Job>()
            every { job.cancel() } just runs
            worker.job = job

            // when
            worker.cancel()

            // then
            verify(exactly = 1) { job.cancel() }
        }
    }

    @Nested
    inner class IsInProgressTest {

        @Test
        fun `job is null`() {
            // given
            worker.job = null

            // when
            val result = worker.isInProgress()

            // then
            assertThat(result).isFalse
        }

        @Test
        fun `job is cancelled`() {
            // given
            val job = mockk<Job>()
            every { job.isActive } returns false
            worker.job = job

            // when
            val result = worker.isInProgress()

            // then
            assertThat(result).isFalse
            verify { job.isActive }
        }

        @Test
        fun `job is in progress`() {
            // given
            val job = mockk<Job>()
            every { job.isActive } returns true
            worker.job = job

            // when
            val result = worker.isInProgress()

            // then
            assertThat(result).isTrue
            verify { job.isActive }
        }
    }
}
