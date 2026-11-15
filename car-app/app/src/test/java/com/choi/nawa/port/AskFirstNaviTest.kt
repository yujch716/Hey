package com.choi.nawa.port

import com.choi.nawa.data.LatLng
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AskFirstNaviTest {
    private class RecordingNavi : NaviPort {
        val calls = mutableListOf<String>()
        override val arrivals = MutableSharedFlow<String>()
        override suspend fun start() {}
        override fun requestRoute(name: String, position: LatLng) { calls += "route $name" }
        override fun cancelRoute() { calls += "cancel" }
        override suspend fun destination(): NaviDestination? = null
        override suspend fun currentLocation(): Fix? = null
        override fun release() {}
    }

    private val p1 = LatLng(37.4, 127.1)
    private val p2 = LatLng(37.41, 127.11)

    @Test
    fun `a destination waits for 예 before the navi gets it`() {
        val inner = RecordingNavi()
        val navi = AskFirstNavi(inner)

        navi.requestRoute("1번 정류지", p1)
        assertEquals(RouteRequest("1번 정류지", p1), navi.pending.value)
        assertEquals(emptyList<String>(), inner.calls)

        navi.confirm()
        assertNull(navi.pending.value)
        assertEquals(listOf("route 1번 정류지"), inner.calls)
    }

    @Test
    fun `아니오 leaves the navi alone, and a newer destination replaces an unanswered one`() {
        val inner = RecordingNavi()
        val navi = AskFirstNavi(inner)

        navi.requestRoute("1번 정류지", p1)
        navi.dismiss()
        navi.requestRoute("2번 정류지", p1)
        navi.requestRoute("3번 정류지", p2)
        navi.confirm()

        assertEquals(listOf("route 3번 정류지"), inner.calls)
    }

    @Test
    fun `ending the trip drops the question and cancels the navi`() {
        val inner = RecordingNavi()
        val navi = AskFirstNavi(inner)

        navi.requestRoute("3번 정류지", p2)
        navi.cancelRoute()

        assertNull(navi.pending.value)
        assertEquals(listOf("cancel"), inner.calls)
    }
}
