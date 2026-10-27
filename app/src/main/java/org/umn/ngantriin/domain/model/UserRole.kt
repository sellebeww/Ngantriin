package org.umn.ngantriin.domain.model

/** Menentukan tujuan setelah login. */
enum class UserRole {
    CUSTOMER,
    STAFF,

    /** Meninjau pengajuan restoran. Hanya database yang bisa memberikannya (0021). */
    ADMIN;

    val wireValue: String get() = name

    companion object {
        fun fromWire(value: String?): UserRole =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: CUSTOMER
    }
}
