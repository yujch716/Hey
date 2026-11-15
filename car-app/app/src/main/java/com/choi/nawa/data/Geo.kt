package com.choi.nawa.data

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Same formulas as server/src/common/geo.ts, so both sides agree on distances. */
object Geo {
    private const val R = 6371000.0

    private fun rad(d: Double) = d * PI / 180

    fun distance(a: LatLng, b: LatLng): Double {
        val dLat = rad(b.lat - a.lat)
        val dLng = rad(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * R * asin(sqrt(h))
    }

    /** Degrees 0..360, 0 = north */
    fun bearing(a: LatLng, b: LatLng): Double {
        val y = sin(rad(b.lng - a.lng)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lng - a.lng))
        return (atan2(y, x) * 180 / PI + 360) % 360
    }

    /** Cumulative distance (m) at each vertex of a polyline */
    fun cumulative(route: List<LatLng>): List<Double> {
        val out = ArrayList<Double>(route.size)
        route.forEachIndexed { i, p -> out.add(if (i == 0) 0.0 else out[i - 1] + distance(route[i - 1], p)) }
        return out
    }

    /** Point `meters` along the polyline, with the heading of the segment it is on */
    fun pointAlong(route: List<LatLng>, cum: List<Double>, meters: Double): Pair<LatLng, Double> {
        if (route.size < 2) return (route.firstOrNull() ?: LatLng(0.0, 0.0)) to 0.0
        val m = meters.coerceIn(0.0, cum.last())
        var i = 1
        while (i < cum.size - 1 && cum[i] < m) i++
        val a = route[i - 1]
        val b = route[i]
        val seg = cum[i] - cum[i - 1]
        val t = if (seg == 0.0) 0.0 else (m - cum[i - 1]) / seg
        return LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t) to bearing(a, b)
    }

    /** Index of the route vertex closest to `p` (to find where a navi destination sits on the route) */
    fun nearestIndex(route: List<LatLng>, p: LatLng): Int =
        route.indices.minByOrNull { distance(route[it], p) } ?: 0
}
