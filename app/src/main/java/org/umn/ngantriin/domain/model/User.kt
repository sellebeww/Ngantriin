package org.umn.ngantriin.domain.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val role: UserRole = UserRole.CUSTOMER
) {
    val displayName: String
        get() = name.ifBlank { email.substringBefore('@') }

    /** Two-letter monogram for the avatar placeholder. */
    val initials: String
        get() = displayName
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "N" }
}
