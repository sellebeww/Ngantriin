package org.umn.ngantriin.data.remote.datasource

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Section 36. Subscribes to Postgres changes and emits a bare tick per change.
 *
 * Deltas are deliberately discarded: the repository re-reads the rows it cares
 * about on every tick. That costs one small query but means a dropped,
 * duplicated or out-of-order event can never leave the screen showing a queue
 * position the database does not actually have.
 *
 * The flow is conflated because a burst of row changes still only warrants one
 * refetch.
 */
internal fun SupabaseClient.postgresChanges(
    channelId: String,
    table: String,
    schema: String = "public",
    filterColumn: String? = null,
    filterOperator: FilterOperator = FilterOperator.EQ,
    filterValue: String? = null
): Flow<Unit> = channelFlow {
    // `channel(id)` returns a cached channel if one with this id is already
    // registered (io.github.jan.supabase.realtime.RealtimeImpl.channel()).
    // A previous collection of this same flow removes its channel in a
    // `finally` block, but that cleanup runs asynchronously — if a new
    // collection starts (e.g. a screen restarting its lifecycle-scoped flow)
    // before that cleanup finishes, reusing `channelId` here would hand back
    // an already-joined channel, and postgresChangeFlow() throws
    // IllegalStateException on an already-joined channel. A unique suffix
    // guarantees every collection gets its own fresh channel.
    val realtimeChannel = channel("$channelId:${UUID.randomUUID()}")

    val changes = realtimeChannel.postgresChangeFlow<PostgresAction>(schema = schema) {
        this.table = table
        if (filterColumn != null && filterValue != null) {
            filter(filterColumn, filterOperator, filterValue)
        }
    }

    // Collection has to be running before subscribe(), otherwise events that
    // arrive immediately after joining the channel can slip past unobserved.
    val collector = launch { changes.collect { trySend(Unit) } }

    try {
        realtime.connect()
        realtimeChannel.subscribe()
        awaitCancellation()
    } finally {
        collector.cancel()
        withContext(NonCancellable) {
            runCatching { realtimeChannel.unsubscribe() }
                .onFailure { Log.w("Ngantriin", "unsubscribe($channelId) failed", it) }
            runCatching { removeChannel(realtimeChannel) }
        }
    }
}.conflate()

private suspend fun SupabaseClient.removeChannel(
    channel: io.github.jan.supabase.realtime.RealtimeChannel
) = realtime.removeChannel(channel)
