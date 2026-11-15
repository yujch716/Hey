package com.choi.nawa.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nawa")

/** Saved vehicle and app settings (DataStore). */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    /** null until the driver registers the car */
    val vehicle: Flow<VehicleProfile?> = store.data.map { p ->
        p[VEHICLE]?.let { runCatching { NawaJson.decodeFromString<VehicleProfile>(it) }.getOrNull() }
    }

    /** Demo mode swaps the Pleos SDKs for a simulated car (Fake ports) */
    val demoMode: Flow<Boolean> = store.data.map { it[DEMO] ?: false }

    /** Demo car speed multiplier: 1 or 5 */
    val demoSpeed: Flow<Int> = store.data.map { it[DEMO_SPEED] ?: 1 }

    suspend fun saveVehicle(v: VehicleProfile) = store.edit { it[VEHICLE] = NawaJson.encodeToString(v) }
    suspend fun setDemoMode(on: Boolean) = store.edit { it[DEMO] = on }
    suspend fun setDemoSpeed(x: Int) = store.edit { it[DEMO_SPEED] = x }

    private companion object {
        val VEHICLE = stringPreferencesKey("vehicle")
        val DEMO = booleanPreferencesKey("demo_mode")
        val DEMO_SPEED = intPreferencesKey("demo_speed")
    }
}
