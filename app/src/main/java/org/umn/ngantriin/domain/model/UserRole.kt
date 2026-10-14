package org.umn.ngantriin.domain.model

/** Drives the post-login destination. */
enum class UserRole {
    CUSTOMER,
    STAFF;

    val wireValue: String get() = name

    companion object {
        fun fromWire(value: String?): UserRole =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: CUSTOMER
    }
}
