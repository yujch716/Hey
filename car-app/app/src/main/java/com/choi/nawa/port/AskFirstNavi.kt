package com.choi.nawa.port

import com.choi.nawa.data.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A destination waiting for the driver's answer to "내장 내비로 길안내를 시작할까요?" */
data class RouteRequest(val name: String, val position: LatLng)

/**
 * The built-in navi, but each new destination waits for the driver's 예 on the app's own screen
 * instead of the navi taking over the screen right away. 아니오 leaves the navi as it is; the trip goes on.
 * Arrivals, destination and location still come straight from the wrapped navi.
 */
class AskFirstNavi(private val inner: NaviPort) : NaviPort {
    private val _pending = MutableStateFlow<RouteRequest?>(null)

    /** The destination to ask about, or null when there is nothing to ask */
    val pending: StateFlow<RouteRequest?> = _pending.asStateFlow()

    override val arrivals = inner.arrivals

    override suspend fun start() = inner.start()

    /** Asks instead of routing. A newer destination replaces an unanswered one. */
    override fun requestRoute(name: String, position: LatLng) {
        _pending.value = RouteRequest(name, position)
    }

    /** 예: route the built-in navi to the destination being asked about */
    fun confirm() {
        val r = _pending.value ?: return
        _pending.value = null
        inner.requestRoute(r.name, r.position)
    }

    /** 아니오 */
    fun dismiss() {
        _pending.value = null
    }

    override fun cancelRoute() {
        _pending.value = null
        inner.cancelRoute()
    }

    override suspend fun destination() = inner.destination()
    override suspend fun currentLocation() = inner.currentLocation()

    override fun release() {
        _pending.value = null
        inner.release()
    }
}
