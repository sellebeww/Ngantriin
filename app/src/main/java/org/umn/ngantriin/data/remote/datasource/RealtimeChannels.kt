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
 * Berlangganan perubahan Postgres dan mengirim satu tick kosong per perubahan.
 *
 * Isi perubahan sengaja dibuang: repository membaca ulang baris yang
 * dibutuhkan di setiap tick. Biayanya satu query kecil, tapi event yang
 * hilang, dobel, atau tidak berurutan tidak akan membuat layar menampilkan
 * posisi antrean yang tidak ada di database.
 *
 * Flow-nya di-conflate karena satu rentetan perubahan baris cukup
 * memicu satu kali baca ulang.
 */
internal fun SupabaseClient.postgresChanges(
    channelId: String,
    table: String,
    schema: String = "public",
    filterColumn: String? = null,
    filterOperator: FilterOperator = FilterOperator.EQ,
    filterValue: String? = null
): Flow<Unit> = channelFlow {
    // `channel(id)` mengembalikan channel yang di-cache kalau id yang sama sudah
    // terdaftar (io.github.jan.supabase.realtime.RealtimeImpl.channel()).
    // Collect sebelumnya dari flow ini menghapus channel-nya di blok `finally`,
    // tapi pembersihan itu jalan asinkron. Kalau collect baru mulai (misalnya
    // layar memulai ulang flow sesuai lifecycle) sebelum pembersihan selesai,
    // memakai `channelId` yang sama akan mengembalikan channel yang sudah join,
    // dan postgresChangeFlow() melempar IllegalStateException untuk channel
    // yang sudah join. Suffix unik menjamin tiap collect dapat channel baru.
    val realtimeChannel = channel("$channelId:${UUID.randomUUID()}")

    val changes = realtimeChannel.postgresChangeFlow<PostgresAction>(schema = schema) {
        this.table = table
        if (filterColumn != null && filterValue != null) {
            filter(filterColumn, filterOperator, filterValue)
        }
    }

    // Collect harus sudah jalan sebelum subscribe(), kalau tidak event yang
    // datang tepat setelah join channel bisa terlewat.
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
