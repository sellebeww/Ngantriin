package org.umn.ngantriin.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Jam HP bisa meleset beberapa menit, sedangkan `check_in_deadline` ditentukan
 * server. Setiap fetch posisi membawa `now()` server, dan selisihnya dari jam
 * lokal disimpan di sini supaya hitung mundur cocok dengan yang akan ditegakkan
 * server. Selisihnya paling banyak satu kali bolak-balik network.
 */
object ServerClock {
    @Volatile
    private var offset: Duration = Duration.ZERO

    fun now(): Instant = Instant.now().plus(offset)

    fun sync(serverNow: Instant, receivedAt: Instant = Instant.now()) {
        offset = Duration.between(receivedAt, serverNow)
    }

    fun reset() {
        offset = Duration.ZERO
    }
}
