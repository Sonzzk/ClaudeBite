package com.sonzzk.claudebite.system

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

/** Reads today's steps from Health Connect (if installed and allowed). Never writes anything. */
object Steps {
    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(context: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(permissions)
    }.getOrDefault(false)

    /** Steps today, or null when Health Connect isn't available or allowed. */
    suspend fun today(context: Context): Long? = runCatching {
        if (!available(context) || !granted(context)) return null
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant()
        val end = java.time.Instant.now()
        val result = HealthConnectClient.getOrCreate(context).aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)),
        )
        result[StepsRecord.COUNT_TOTAL] ?: 0L
    }.getOrNull()

    /** Rough kcal from walking: about 0.04 kcal per step. */
    fun kcal(steps: Long): Int = (steps * 0.04).toInt()
}
