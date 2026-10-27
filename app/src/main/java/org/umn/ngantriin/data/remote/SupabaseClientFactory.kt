package org.umn.ngantriin.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import org.umn.ngantriin.BuildConfig

/**
 * Membuat client Supabase dari kredensial di local.properties.
 * Mengembalikan null kalau kredensialnya tidak ada, dan itu jadi tanda bagi
 * DI container untuk memakai backend demo.
 */
object SupabaseClientFactory {

    const val RECOVERY_SCHEME = "ngantriin"
    const val RECOVERY_HOST = "auth"

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun createOrNull(): SupabaseClient? {
        if (!isConfigured) return null
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                // Email reset password menaut ke ngantriin://auth, lalu manifest
                // mengarahkannya kembali ke MainActivity.
                scheme = RECOVERY_SCHEME
                host = RECOVERY_HOST
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
            defaultSerializer = KotlinXSerializer(json)
        }
    }
}
