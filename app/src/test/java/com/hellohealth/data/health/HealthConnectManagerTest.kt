package com.hellohealth.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for the Health Connect permission gate ([HealthConnectManager.isConnected]).
 *
 * The original bug: the gate required `android.permission.health.READ_EXERCISE_ROUTES` in its
 * `containsAll` check, but that string is never returned by `getGrantedPermissions()` — reading
 * routes is a per-session consent flow (ExerciseRouteResult.ConsentRequired), not a standing
 * permission. That made the gate permanently false on EVERY API level even after the user granted
 * everything, so the "Connect Now" prompt never went away.
 *
 * `isConnected` is a pure function so this needs no live HealthConnectClient. Robolectric is used
 * only because the manager touches android.* types at construction.
 */
@RunWith(RobolectricTestRunner::class)
class HealthConnectManagerTest {

    private val manager = HealthConnectManager(null, ApplicationProvider.getApplicationContext())

    private val essentialGranted = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        // P3 vitals perms are part of the gate now (all standing record-read permissions).
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class)
    )

    @Test
    fun `essential read permissions granted marks connected without routes string`() {
        // Deliberately no READ_EXERCISE_ROUTES — mirrors what getGrantedPermissions() actually returns.
        assertTrue(manager.isConnected(essentialGranted))
    }

    @Test
    fun `extra granted permissions still connected`() {
        assertTrue(manager.isConnected(essentialGranted + "android.permission.health.READ_HEART_RATE"))
    }

    @Test
    fun `missing an essential permission is not connected`() {
        assertFalse(
            manager.isConnected(essentialGranted - HealthPermission.getReadPermission(StepsRecord::class))
        )
    }

    @Test
    fun `denying a single vitals permission drops the gate`() {
        // Decision #4: the five vitals perms join the gate. Denying any one correctly reports
        // not-connected (a denied perm is a real disconnect, unlike granted-but-empty data).
        assertFalse(
            manager.isConnected(
                essentialGranted - HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
            )
        )
    }

    @Test
    fun `empty grants is not connected`() {
        assertFalse(manager.isConnected(emptySet()))
    }
}
