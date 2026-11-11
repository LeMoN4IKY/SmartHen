package com.smarthen.domain

enum class ConnectionState { CONNECTED, OFFLINE, SERVER_UNAVAILABLE, CONNECTING }
enum class CommandBlockReason {
    CONNECTION_UNAVAILABLE, TARGET_MISMATCH, NOT_LIVE, TELEMETRY_UNAVAILABLE,
    NOT_AUTHORIZED, NOT_SUPPORTED, COMMAND_PENDING,
}

sealed interface CommandEligibility {
    data object Eligible : CommandEligibility
    data class Blocked(val reason: CommandBlockReason) : CommandEligibility
}

/**
 * Local preflight only: the server must independently authorize and validate every command.
 * Re-evaluate immediately before sending; eligibility never implies execution or acknowledgement.
 */
fun commandEligibility(
    target: DeviceId,
    snapshot: SensorSnapshot?,
    connection: ConnectionState,
    nowEpochSeconds: Long,
    maximumAgeSeconds: Long,
    authorized: Boolean,
    supported: Boolean,
    commandPending: Boolean,
): CommandEligibility {
    require(maximumAgeSeconds > 0)
    require(nowEpochSeconds >= 0)
    fun blocked(reason: CommandBlockReason) = CommandEligibility.Blocked(reason)
    if (connection != ConnectionState.CONNECTED) return blocked(CommandBlockReason.CONNECTION_UNAVAILABLE)
    if (!authorized) return blocked(CommandBlockReason.NOT_AUTHORIZED)
    if (!supported) return blocked(CommandBlockReason.NOT_SUPPORTED)
    if (commandPending) return blocked(CommandBlockReason.COMMAND_PENDING)
    if (snapshot == null) return blocked(CommandBlockReason.TELEMETRY_UNAVAILABLE)
    if (snapshot.device != target) return blocked(CommandBlockReason.TARGET_MISMATCH)
    if (snapshot.source != ReadingSource.LIVE) return blocked(CommandBlockReason.NOT_LIVE)
    if (snapshot.freshness(nowEpochSeconds, maximumAgeSeconds) != Freshness.FRESH) {
        return blocked(CommandBlockReason.TELEMETRY_UNAVAILABLE)
    }
    return CommandEligibility.Eligible
}
