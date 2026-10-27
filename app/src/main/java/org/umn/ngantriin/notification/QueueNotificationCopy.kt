package org.umn.ngantriin.notification

import android.content.Context
import org.umn.ngantriin.R
import org.umn.ngantriin.domain.model.ActiveQueue
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import java.time.Instant

/**
 * Satu tempat yang menentukan isi tiap notifikasi, dipakai bersama oleh
 * jalur FCM, scheduler lokal, dan service pelacakan, supaya kalimatnya
 * tidak melenceng satu sama lain.
 */
object QueueNotificationCopy {

    /** Angka yang boleh disebut pesan. Tidak ada yang menyebut nama restoran. */
    data class Facts(
        val queueId: String,
        val queueNumber: String,
        val peopleAhead: Int = 0,
        val estimatedWaitMinutes: Int = 0,
        val checkInWindowMinutes: Int = QueueMath.DEFAULT_CHECK_IN_WINDOW_MINUTES
    )

    /**
     * Id deterministik, mengikuti constraint `unique (queue_id, type)` di
     * skema: milestone yang sama untuk tiket yang sama selalu baris yang sama,
     * jadi tidak bisa terkirim dua kali.
     */
    fun idFor(queueId: String, type: NotificationType) = "$queueId:${type.name}"

    /**
     * Notifikasi dibuat di luar Compose, jadi ini meniru
     * [org.umn.ngantriin.ui.components.waitTimeText] dengan [Context.getString]
     * langsung, bukan `stringResource`. [context] sudah membawa bahasa yang
     * dipilih di Pengaturan -> Bahasa.
     */
    fun waitTimeText(context: Context, minutes: Int): String = when {
        minutes <= 0 -> context.getString(R.string.wait_time_none)
        minutes < 60 -> context.getString(R.string.wait_time_minutes, minutes)
        minutes % 60 == 0 -> context.getString(R.string.wait_time_hours, minutes / 60)
        else -> context.getString(R.string.wait_time_hours_minutes, minutes / 60, minutes % 60)
    }

    fun build(
        context: Context,
        userId: String,
        queue: ActiveQueue,
        type: NotificationType
    ): AppNotification = build(
        context = context,
        userId = userId,
        facts = Facts(
            queueId = queue.entry.id,
            queueNumber = queue.queueNumber,
            peopleAhead = queue.peopleAhead,
            estimatedWaitMinutes = queue.estimatedWaitMinutes,
            checkInWindowMinutes = queue.restaurant.checkInWindowMinutes
        ),
        type = type
    )

    /** Untuk tiket yang sudah berakhir sehingga tidak ada antrean di sekitarnya. */
    fun build(
        context: Context,
        userId: String,
        entry: QueueEntry,
        checkInWindowMinutes: Int,
        type: NotificationType
    ): AppNotification = build(
        context = context,
        userId = userId,
        facts = Facts(
            queueId = entry.id,
            queueNumber = entry.queueNumber,
            checkInWindowMinutes = checkInWindowMinutes
        ),
        type = type
    )

    /**
     * Privasi: lock screen bisa dilihat siapa saja, notifikasi terbaca tanpa
     * membuka kunci. Karena itu tidak ada yang dibuat di sini yang menyebut nama
     * restoran. Detail itu baru muncul setelah notifikasi diketuk dan aplikasi terbuka.
     */
    fun build(
        context: Context,
        userId: String,
        facts: Facts,
        type: NotificationType,
        id: String = idFor(facts.queueId, type)
    ): AppNotification {
        val (title, body) = text(context, facts, type)
        return AppNotification(
            id = id,
            userId = userId,
            queueId = facts.queueId,
            title = title,
            body = body,
            type = type,
            isRead = false,
            createdAt = Instant.now()
        )
    }

    private fun text(context: Context, facts: Facts, type: NotificationType): Pair<String, String> =
        when (type) {
            NotificationType.ALMOST_THERE -> context.getString(R.string.push_almost_there_title) to
                aheadBody(context, facts)

            NotificationType.RETURN_NOW -> context.getString(R.string.push_return_now_title) to
                context.getString(R.string.push_return_now_body)

            NotificationType.CALLED -> context.getString(R.string.push_called_title) to
                context.getString(R.string.push_called_body, facts.queueNumber, facts.checkInWindowMinutes)

            NotificationType.CHECKED_IN -> context.getString(R.string.push_checked_in_title) to
                context.getString(R.string.push_checked_in_body)

            NotificationType.TABLE_READY -> context.getString(R.string.push_table_ready_title) to
                context.getString(R.string.push_table_ready_body, facts.queueNumber)

            NotificationType.COMPLETED -> context.getString(R.string.push_completed_title) to
                context.getString(R.string.push_completed_body)

            NotificationType.CANCELLED -> context.getString(R.string.push_cancelled_title) to
                context.getString(R.string.push_cancelled_body)

            NotificationType.EXPIRED -> context.getString(R.string.push_expired_title) to
                context.getString(R.string.push_expired_body, facts.queueNumber, facts.checkInWindowMinutes)

            NotificationType.QUEUE_JOINED -> context.getString(R.string.push_joined_title) to
                context.resources.getQuantityString(
                    R.plurals.push_joined_body,
                    facts.peopleAhead,
                    facts.queueNumber,
                    facts.peopleAhead,
                    waitTimeText(context, facts.estimatedWaitMinutes)
                )

            NotificationType.POSITION_UPDATE ->
                context.getString(R.string.push_queue_update_title) to aheadBody(context, facts)
        }

    private fun aheadBody(context: Context, facts: Facts): String =
        context.resources.getQuantityString(
            R.plurals.push_almost_there_body,
            facts.peopleAhead,
            facts.peopleAhead,
            waitTimeText(context, facts.estimatedWaitMinutes)
        )
}
