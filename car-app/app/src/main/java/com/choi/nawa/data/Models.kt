package com.choi.nawa.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LatLng(val lat: Double, val lng: Double)

/** What the passenger looks for at the curb. Entered by the driver: the Vehicle SDK has no model or colour. */
@Serializable
data class VehicleProfile(
    val model: String,
    val colorName: String,
    val colorHex: String,
    val bodyType: BodyType,
    val plate: String,
)

@Serializable
enum class BodyType {
    @SerialName("sedan") SEDAN,
    @SerialName("suv") SUV,
    @SerialName("truck") TRUCK,
}

@Serializable
enum class StopKind {
    @SerialName("waypoint") WAYPOINT,
    @SerialName("pickup") PICKUP,
}

/** Result of GET /places/search */
@Serializable
data class Place(val name: String, val address: String, val lat: Double, val lng: Double) {
    val position get() = LatLng(lat, lng)
}

/** A stop while the driver is still planning, before the trip exists on the server. */
data class StopDraft(
    val id: Long,
    val place: Place,
    val kind: StopKind,
    /** Waypoints only, e.g. "카페". Pickups carry no name (privacy). */
    val waypointName: String,
)

// ── Server API bodies ──

/** Pickup stops get this neutral label: the web shows "내 위치"/"픽업" per passenger. */
const val PICKUP_LABEL = "픽업"
const val MAX_LABEL_LENGTH = 12

@Serializable
data class CreateTripRequest(
    val vehicle: VehicleProfile,
    val origin: LatLng,
    val stops: List<CreateStop>,
)

@Serializable
data class CreateStop(val order: Int, val kind: StopKind, val label: String, val position: LatLng)

@Serializable
data class CreateTripResponse(
    val tripId: String,
    val driverToken: String,
    val shares: List<Share>,
    val route: List<LatLng>,
    val stops: List<PlannedStop>,
)

@Serializable
data class Share(val stopId: String, val token: String, val url: String) {
    /** The QR opens the "send this link" page on the driver's phone, not the passenger page itself. */
    val sendUrl get() = url.replace("/t/", "/send/")
}

@Serializable
data class PlannedStop(
    val order: Int,
    val kind: StopKind,
    val label: String,
    val position: LatLng,
    val routeIndex: Int,
    val legSeconds: Int,
    val etaSeconds: Int,
)

@Serializable
data class LocationBody(
    val lat: Double,
    val lng: Double,
    val heading: Double,
    val currentLegEtaSeconds: Int,
    val currentStopOrder: Int,
    val ts: Long,
)

@Serializable
data class EventBody(val type: String, val stopOrder: Int? = null, val by: String? = null)

// ── Driver WebSocket payloads ──

@Serializable
data class PassengerReply(val stopOrder: Int, val type: String, val position: LatLng? = null)

@Serializable
data class PassengerLocation(val stopOrder: Int, val lat: Double, val lng: Double)
