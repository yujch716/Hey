package com.choi.nawa.sdkcheck

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData

/**
 * Car position: Pleos FusedLocation first ("준비 중" per the docs), and plain AAOS LocationManager
 * as the fallback. Logs the gap between callbacks so we know the real update rate.
 */
@SuppressLint("MissingPermission") // requested at runtime from the screen; failures are logged
class LocationProbe(private val context: Context) {
    private val fusedSection = "FUSED"
    private val lmSection = "LOCMGR"

    private val fused = FusedLocation(context.applicationContext)
    private var fusedInitialized = false
    private var lastFusedAt = 0L

    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val now = System.currentTimeMillis()
            val gap = if (lastFusedAt == 0L) "first" else "+${now - lastFusedAt}ms"
            lastFusedAt = now
            ProbeLog.log(fusedSection, "◀ onChanged ($gap) $location")
        }

        override fun onError(exception: Exception) =
            ProbeLog.log(fusedSection, "◀ onError ${exception.javaClass.name}: ${exception.message}")
    }

    fun checkFused() = probe(fusedSection, "isSupported") {
        val supported = fused.isSupported()
        ProbeLog.log(fusedSection, "isSupported() = $supported")
        if (supported && !fusedInitialized) {
            fused.initialize()
            fusedInitialized = true
            ProbeLog.log(fusedSection, "initialize() called")
        }
    }

    fun fusedLastKnown() = probe(fusedSection, "getLastKnownLocation") {
        ProbeLog.log(fusedSection, "getLastKnownLocation()")
        fused.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) = ProbeLog.log(fusedSection, "◀ lastKnown $location")
            override fun onError(exception: Exception) =
                ProbeLog.log(fusedSection, "◀ lastKnown onError ${exception.javaClass.name}: ${exception.message}")
        })
    }

    fun fusedWatch(on: Boolean) = probe(fusedSection, if (on) "register" else "unregister") {
        if (on) {
            lastFusedAt = 0L
            fused.registerFusedLocationCallback(fusedListener)
        } else {
            fused.unregisterFusedLocationCallback(fusedListener)
        }
        ProbeLog.log(fusedSection, if (on) "registerFusedLocationCallback()" else "unregisterFusedLocationCallback()")
    }

    // ── AAOS LocationManager fallback ──

    private val lm = context.getSystemService(LocationManager::class.java)
    private var lastLmAt = 0L
    private val lmListener = LocationListener { loc ->
        val now = System.currentTimeMillis()
        val gap = if (lastLmAt == 0L) "first" else "+${now - lastLmAt}ms"
        lastLmAt = now
        ProbeLog.log(lmSection, "◀ ${loc.provider} ($gap) ${describe(loc)}")
    }

    fun locationManagerSnapshot() = probe(lmSection, "snapshot") {
        val providers = lm.allProviders
        ProbeLog.log(lmSection, "providers=${providers.map { "$it(${if (lm.isProviderEnabled(it)) "on" else "off"})" }}")
        for (p in providers) {
            val loc = lm.getLastKnownLocation(p)
            ProbeLog.log(lmSection, "lastKnown[$p] = ${loc?.let(::describe)}")
        }
    }

    fun locationManagerWatch(on: Boolean) = probe(lmSection, if (on) "requestLocationUpdates" else "removeUpdates") {
        if (on) {
            lastLmAt = 0L
            val provider = if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else lm.allProviders.first()
            lm.requestLocationUpdates(provider, 1000L, 0f, lmListener, Looper.getMainLooper())
            ProbeLog.log(lmSection, "requestLocationUpdates($provider, 1000ms)")
        } else {
            lm.removeUpdates(lmListener)
            ProbeLog.log(lmSection, "removeUpdates()")
        }
    }

    fun release() {
        probe(fusedSection, "release") {
            if (fusedInitialized) {
                fused.unregisterFusedLocationCallback(fusedListener)
                fused.release()
            }
        }
        probe(lmSection, "release") { lm.removeUpdates(lmListener) }
    }

    /** Speed is the one stop/go signal the Vehicle SDK lacks, so print it when the provider has it */
    private fun describe(l: Location) =
        "lat=${l.latitude} lng=${l.longitude} acc=${l.accuracy}m " +
            "speed=${if (l.hasSpeed()) "${l.speed}m/s" else "n/a"} bearing=${if (l.hasBearing()) l.bearing else "n/a"} time=${l.time}"
}
