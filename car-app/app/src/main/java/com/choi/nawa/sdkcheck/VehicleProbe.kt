package com.choi.nawa.sdkcheck

import android.annotation.SuppressLint
import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener

/** Doors we care about for "a passenger got in": driver (row 1 left in Korea), passenger, rear seats. */
private val DOORS = listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT, DoorValue.REAR)

/**
 * Vehicle SDK: per-door open state and its callbacks, what can tell "stopped" (gear, brake),
 * what CarInfo returns, and every capability check we can call with CAR_DOORS / CAR_DRIVE / CAR_INFO.
 */
@SuppressLint("MissingPermission") // declared in the manifest; the probe logs any VehicleException
class VehicleProbe(context: Context) {
    private val section = "VEHICLE"
    private val vehicle = Vehicle(context.applicationContext)
    private val door by lazy { vehicle.getDoor() }
    private val gear by lazy { vehicle.getGear() }
    private val brake by lazy { vehicle.getBrake() }
    private val drivingMode by lazy { vehicle.getDrivingMode() }
    private val carInfo by lazy { vehicle.getCarInfo() }

    private val doorListeners = DOORS.associateWith { value ->
        object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) = log("◀ door $value position=$pos")
            override fun onFailed(e: Exception) = fail("door $value listener", e)
        }
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = log("◀ gear=$gear")
        override fun onFailed(e: Exception) = fail("gear listener", e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = log("◀ brakePedal=$status")
        override fun onFailed(e: Exception) = fail("brake listener", e)
    }

    fun initialize() = probe(section, "initialize") {
        vehicle.initialize()
        log("initialize() called (no callback in this SDK; service binds asynchronously)")
    }

    fun capabilities() = probe(section, "capabilities") {
        door.checkDoorPositionCapabilities(
            { cfg, areas -> log("checkDoorPositionCapabilities: $cfg areas=${areas.map { (a, b) -> "${a.values.toList()}=${b.minValue}..${b.maxValue}" }}") },
            { fail("checkDoorPositionCapabilities", it) },
        )
        door.checkDoorLockCapabilities(
            { cfg, areas -> log("checkDoorLockCapabilities: $cfg areas=${areas.map { it.values.toList() }}") },
            { fail("checkDoorLockCapabilities", it) },
        )
        brake.checkBrakePedalStatusCapabilities({ log("checkBrakePedalStatusCapabilities: $it") }, { fail("checkBrakePedalStatusCapabilities", it) })
        drivingMode.checkDrivingModeStateCapabilities({ log("checkDrivingModeStateCapabilities: $it") }, { fail("checkDrivingModeStateCapabilities", it) })
        carInfo.checkVINInfoCapabilities({ log("checkVINInfoCapabilities: $it") }, { fail("checkVINInfoCapabilities", it) })
        carInfo.checkCRNInfoCapabilities({ log("checkCRNInfoCapabilities: $it") }, { fail("checkCRNInfoCapabilities", it) })
        log("(Gear has no checkXXXCapabilities API)")
    }

    fun readDoors() = probe(section, "readDoors") {
        for (value in DOORS) {
            door.getCurrentDoorPosition(DoorArea(value), { log("door $value position=$it") }, { fail("getCurrentDoorPosition($value)", it) })
        }
    }

    fun watchDoors(on: Boolean) = probe(section, "watchDoors") {
        for ((value, listener) in doorListeners) {
            if (on) door.registerDoorPosition(DoorArea(value), listener) else door.unregisterDoorPosition(DoorArea(value), listener)
        }
        log(if (on) "registerDoorPosition for $DOORS" else "unregisterDoorPosition")
    }

    fun readDriveState() = probe(section, "readDriveState") {
        gear.getCurrentGearSelectionState({ log("gear=$it") }, { fail("getCurrentGearSelectionState", it) })
        brake.getCurrentBrakePedalStatus({ log("brakePedal=$it") }, { fail("getCurrentBrakePedalStatus", it) })
        drivingMode.getCurrentDrivingModeState({ log("drivingMode=$it") }, { fail("getCurrentDrivingModeState", it) })
    }

    fun watchDriveState(on: Boolean) = probe(section, "watchDriveState") {
        if (on) {
            gear.registerGearSelection(gearListener)
            brake.registerBrakePedalStatus(brakeListener)
        } else {
            gear.unregisterGearSelection(gearListener)
            brake.unregisterBrakePedalStatus(brakeListener)
        }
        log(if (on) "registered gear + brake pedal" else "unregistered gear + brake pedal")
    }

    fun readCarInfo() = probe(section, "readCarInfo") {
        carInfo.getVINInfo({ log("VIN=$it") }, { fail("getVINInfo", it) })
        carInfo.getCRNInfo({ log("CRN(차량등록번호)=$it") }, { fail("getCRNInfo", it) })
        log("(CarInfo has only VIN and CRN; model/colour are not in the SDK)")
    }

    fun release() = probe(section, "release") {
        watchDoors(false)
        watchDriveState(false)
        vehicle.release()
    }

    private fun log(message: String) = ProbeLog.log(section, message)
    private fun fail(what: String, e: Exception) = log("$what failed: ${e.javaClass.name}: ${e.message}")
}
