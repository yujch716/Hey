package com.choi.nawa.data

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Everything the car app asks the server. An interface so TripController tests run without a network. */
interface TripApi {
    suspend fun searchPlaces(query: String, near: LatLng?): List<Place>
    suspend fun createTrip(request: CreateTripRequest): CreateTripResponse
    suspend fun sendLocation(tripId: String, driverToken: String, body: LocationBody)
    suspend fun sendEvent(tripId: String, driverToken: String, body: EventBody)
}

class ApiException(val status: Int, message: String) : IOException(message)

val NawaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

class HttpTripApi(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
) : TripApi {
    private val jsonType = "application/json".toMediaType()

    override suspend fun searchPlaces(query: String, near: LatLng?): List<Place> {
        val url = "$baseUrl/places/search".toHttpUrl().newBuilder().addQueryParameter("query", query).apply {
            if (near != null) {
                addQueryParameter("lat", near.lat.toString())
                addQueryParameter("lng", near.lng.toString())
            }
        }.build()
        return NawaJson.decodeFromString(call(Request.Builder().url(url).build()))
    }

    override suspend fun createTrip(request: CreateTripRequest): CreateTripResponse =
        NawaJson.decodeFromString(post("/trips", NawaJson.encodeToString(request), token = null))

    override suspend fun sendLocation(tripId: String, driverToken: String, body: LocationBody) {
        post("/trips/$tripId/location", NawaJson.encodeToString(body), driverToken)
    }

    override suspend fun sendEvent(tripId: String, driverToken: String, body: EventBody) {
        post("/trips/$tripId/events", NawaJson.encodeToString(body), driverToken)
    }

    private suspend fun post(path: String, json: String, token: String?): String {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .post(json.toRequestBody(jsonType))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return call(request)
    }

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(res.code, "${request.method} ${request.url.encodedPath} → ${res.code} ${body.take(200)}")
            body
        }
    }
}

/** The driver side of socket.io /live: passenger replies and shared positions arrive here. */
interface DriverChannel {
    fun connect(tripId: String, driverToken: String, listener: Listener)
    fun close()

    interface Listener {
        fun onConnection(connected: Boolean)
        fun onReply(reply: PassengerReply)
        fun onPassengerLocation(stopOrder: Int, position: LatLng?)
    }
}

class SocketIoDriverChannel(private val baseUrl: String) : DriverChannel {
    private var socket: Socket? = null

    override fun connect(tripId: String, driverToken: String, listener: DriverChannel.Listener) {
        close()
        val s = IO.socket("$baseUrl/live", IO.Options().apply { transports = arrayOf("websocket") })
        s.on(Socket.EVENT_CONNECT) {
            s.emit("join", JSONObject().put("role", "driver").put("tripId", tripId).put("driverToken", driverToken))
            listener.onConnection(true)
        }
        s.on(Socket.EVENT_DISCONNECT) { listener.onConnection(false) }
        s.on(Socket.EVENT_CONNECT_ERROR) { listener.onConnection(false) }
        s.on("reply") { args -> args.firstOrNull()?.let { listener.onReply(NawaJson.decodeFromString(it.toString())) } }
        s.on("passengerLocation") { args ->
            args.firstOrNull()?.let {
                val p = NawaJson.decodeFromString<PassengerLocation>(it.toString())
                listener.onPassengerLocation(p.stopOrder, LatLng(p.lat, p.lng))
            }
        }
        s.on("passengerLocationStopped") { args ->
            (args.firstOrNull() as? JSONObject)?.let { listener.onPassengerLocation(it.getInt("stopOrder"), null) }
        }
        s.connect()
        socket = s
    }

    override fun close() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }
}
