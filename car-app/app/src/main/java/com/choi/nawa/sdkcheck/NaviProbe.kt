package com.choi.nawa.sdkcheck

import android.annotation.SuppressLint
import android.content.Context
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/** A test destination near 강남역. POI ids are left empty on purpose: we don't have real ones. */
data class TestPlace(val name: String, val lat: Double, val lng: Double, val address: String)

val GANGNAM_STATION = TestPlace("강남역", 37.497942, 127.027621, "서울 강남구 강남대로 396")
val YEOKSAM_STATION = TestPlace("역삼역", 37.500622, 127.036456, "서울 강남구 테헤란로 156")

/**
 * NaviHelper: does the built-in navi start guidance, and what does it report back?
 * Every listener callback is logged with its full payload, so we see exactly which events arrive.
 */
@SuppressLint("MissingPermission") // declared in the manifest; the probe logs any SecurityException
class NaviProbe(context: Context) {
    private val section = "NAVI"
    private val navi = NaviHelper(context.applicationContext)

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = event("onRouteStarted", routeStartInfo)
        override fun onRouteEnded() = event("onRouteEnded")
        override fun onRoutingCompleted() = event("onRoutingCompleted")
        override fun onReRouteEnded() = event("onReRouteEnded")
        override fun onRouteOptionChanged(routeOption: RouteOption) = event("onRouteOptionChanged", routeOption)
        override fun onRouteCancelled() = event("onRouteCancelled")
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = event("onWaypointChanged", waypointChangedInfo)
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
            event("onDestinationArrived", destinationArrivedInfo)
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = event("onWayPointArrived", waypointArrivedInfo)
        override fun onBookMarkChanged() = event("onBookMarkChanged")
        override fun onDestinationChanged() = event("onDestinationChanged")
        override fun onError(errorCode: NaviErrorCode) = event("onError", errorCode)
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) =
            event("onCurrentLocationInfo", currentLocationInfo)
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = event("onRouteStateInfo", routeStateInfo)
        override fun onDestinationInfo(destinationInfo: DestinationInfo) = event("onDestinationInfo", destinationInfo)
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = event("onWayPointInfo", waypointInfoList)
        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) =
            event("onRecentDestinationInfo", "${recentDestinationInfoList.size} items")
        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) = event("onBookmarkInfo", "${bookmarkInfoList.size} items")
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = throttled("onCurrentRoadSpeedLimit", speedLimit)
        override fun onCameraAlert(cameraAlertList: List<CameraAlert>) = event("onCameraAlert", cameraAlertList)
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = throttled("onDrivingInfo", drivingInfo)
        override fun onNavigationStatus(status: NavigationStatus) = event("onNavigationStatus", status)
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) = throttled("onTBTInfo", tbtInfoList)
        override fun onChargerOperatorInfo(chargerOperatorList: List<ChargerOperator>) =
            event("onChargerOperatorInfo", "${chargerOperatorList.size} items")
        override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) = throttled("onCautionPointInfo", cautionPointList)
        override fun onTripInfo(tripInfo: TripInfo) = event("onTripInfo", tripInfo)
    }

    fun initialize() = probe(section, "initialize") {
        log("initialize() …")
        navi.initialize(
            onConnected = {
                log("initialize → onConnected")
                navi.addListener(listener)
                log("listener added")
            },
            onFailed = { log("initialize → onFailed: ${it.javaClass.name}: ${it.message}") },
        )
    }

    fun requestRoute(place: TestPlace) = probe(section, "requestRoute") {
        log("requestRoute(${place.name} ${place.lat},${place.lng})")
        navi.requestRoute(
            RouteInfo(
                latitude = place.lat,
                longitude = place.lng,
                poiName = place.name,
                poiId = "",
                address = place.address,
                poiSubId = "0",
            ),
        )
    }

    fun cancelRoute() = probe(section, "cancelRoute") { log("cancelRoute()"); navi.cancelRoute() }
    fun getRouteStateInfo() = probe(section, "getRouteStateInfo") { log("getRouteStateInfo()"); navi.getRouteStateInfo() }
    fun getDestinationInfo() = probe(section, "getDestinationInfo") { log("getDestinationInfo()"); navi.getDestinationInfo() }
    fun getCurrentLocationInfo() = probe(section, "getCurrentLocationInfo") { log("getCurrentLocationInfo()"); navi.getCurrentLocationInfo() }
    fun getTbtInfo() = probe(section, "getTBTInfo") { log("getTBTInfo()"); navi.getTBTInfo() }

    /** Trip estimate to a place without starting guidance (another possible ETA source) */
    fun requestTrip(place: TestPlace) = probe(section, "requestTrip") {
        log("requestTrip(${place.name})")
        navi.requestTrip(TripRequest(latitude = place.lat, longitude = place.lng, poiId = "", poiName = place.name, poiSubId = "0"))
    }

    fun release() = probe(section, "release") {
        navi.removeListener(listener)
        navi.release()
    }

    private fun log(message: String) = ProbeLog.log(section, message)
    private fun event(name: String, payload: Any? = null) = log("◀ $name${payload?.let { " $it" } ?: ""}")

    // Some callbacks fire every second; log the first one and then every 10th
    private val counts = mutableMapOf<String, Int>()
    private fun throttled(name: String, payload: Any?) {
        val n = (counts[name] ?: 0) + 1
        counts[name] = n
        if (n == 1 || n % 10 == 0) event("$name (#$n)", payload)
    }
}
