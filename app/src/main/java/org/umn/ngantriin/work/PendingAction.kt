package org.umn.ngantriin.work

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.umn.ngantriin.data.local.dao.PendingActionDao
import org.umn.ngantriin.data.local.entity.PendingActionEntity
import java.time.Instant

/**
 * Penulisan yang tidak sampai ke backend, ditahan sampai koneksi kembali.
 *
 * Hanya aksi yang tetap benar saat datang terlambat yang diantrekan. Antre
 * tidak termasuk: tiket yang terbit dua puluh menit setelah ketukan lebih
 * buruk daripada tidak ada tiket, jadi join yang gagal dilaporkan ke pengguna.
 * Keluar dari antrean *diantrekan*: guard transisi di database menolak
 * pengulangan terhadap tiket yang sudah dilayani, jadi pengulangan basi
 * hanya no-op yang tidak berbahaya, bukan pembatalan yang salah.
 */
@Serializable
sealed interface PendingAction {

    @Serializable
    @SerialName("leave_queue")
    data class LeaveQueue(val queueId: String) : PendingAction

    @Serializable
    @SerialName("submit_review")
    data class SubmitReview(
        val queueId: String,
        val restaurantId: String,
        val rating: Int,
        val comment: String
    ) : PendingAction

    @Serializable
    @SerialName("mark_notification_read")
    data class MarkNotificationRead(val notificationId: String) : PendingAction
}

/** Menyimpan dan memutar ulang [PendingAction]. */
class PendingActionQueue(private val dao: PendingActionDao) {

    private val json = Json { classDiscriminator = "action" }

    suspend fun enqueue(action: PendingAction) {
        dao.insert(
            PendingActionEntity(
                type = action::class.simpleName.orEmpty(),
                payload = json.encodeToString(PendingAction.serializer(), action),
                createdAt = Instant.now()
            )
        )
    }

    /**
     * Memutar ulang semuanya berurutan. [handler] mengembalikan true kalau aksi
     * sudah selesai, berhasil atau gagal dengan cara yang tidak akan
     * diperbaiki dengan mengulang.
     */
    suspend fun drain(handler: suspend (PendingAction) -> Boolean) {
        dao.dropExhausted(MAX_ATTEMPTS)
        dao.all().forEach { entity ->
            val action = runCatching {
                json.decodeFromString(PendingAction.serializer(), entity.payload)
            }.getOrNull()

            if (action == null) {
                // Baris yang tidak bisa di-parse tidak akan berhasil, buang daripada diulang.
                dao.delete(entity)
                return@forEach
            }

            if (handler(action)) dao.delete(entity) else dao.recordAttempt(entity.id)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
