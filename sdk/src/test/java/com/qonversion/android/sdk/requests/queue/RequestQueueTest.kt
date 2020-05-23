package com.qonversion.android.sdk.requests.queue

import com.qonversion.android.sdk.RequestsQueue
import com.qonversion.android.sdk.logger.StubLogger
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestQueueTest {

    @Test
    fun addOneRequestTest() {
        val queue = RequestsQueue(StubLogger())
        queue.add(Util.QONVERSION_REQUEST)
        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(1, queue.size())
    }

    @Test
    fun addAndPollOneRequestTest() {
        val queue = RequestsQueue(StubLogger())
        queue.add(Util.QONVERSION_REQUEST)
        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(1, queue.size())

        queue.poll()
        Assert.assertTrue(queue.isEmpty())
        Assert.assertEquals(0, queue.size())
    }

    @Test
    fun addAndPollManyRequestTest() {
        val queue = RequestsQueue(StubLogger())

        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)

        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(5, queue.size())

        queue.poll()
        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(4, queue.size())

        queue.poll()
        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(3, queue.size())

        queue.poll()
        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(2, queue.size())

        queue.poll()
        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(1, queue.size())

        queue.poll()
        Assert.assertTrue(queue.isEmpty())
        Assert.assertEquals(0, queue.size())
    }

    @Test
    fun addAndPollMixManyRequestTest() {

        val queue = RequestsQueue(StubLogger())

        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)

        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(2, queue.size())

        queue.poll()

        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(1, queue.size())

        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)

        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(3, queue.size())

        queue.poll()

        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(2, queue.size())

        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)
        queue.add(Util.QONVERSION_REQUEST)

        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(7, queue.size())

        queue.poll()
        queue.poll()
        queue.poll()

        Assert.assertFalse(queue.isEmpty())
        Assert.assertEquals(4, queue.size())
    }
}