package org.umn.ngantriin.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.UserDto
import org.umn.ngantriin.domain.model.UserRole

class UserRoleTest {

    @Test
    fun `every role the database can hold maps to itself`() {
        assertEquals(UserRole.CUSTOMER, UserRole.fromWire("CUSTOMER"))
        assertEquals(UserRole.STAFF, UserRole.fromWire("STAFF"))
        assertEquals(UserRole.ADMIN, UserRole.fromWire("ADMIN"))
    }

    @Test
    fun `case and padding do not matter`() {
        assertEquals(UserRole.ADMIN, UserRole.fromWire(" admin "))
    }

    @Test
    fun `an unreadable role never grants anything`() {
        assertEquals(UserRole.CUSTOMER, UserRole.fromWire(null))
        assertEquals(UserRole.CUSTOMER, UserRole.fromWire(""))
        assertEquals(UserRole.CUSTOMER, UserRole.fromWire("SUPERUSER"))
    }

    @Test
    fun `the profile row decides the role the app routes by`() {
        val admin = UserDto(id = "u", role = "ADMIN").toDomain()
        assertEquals(UserRole.ADMIN, admin.role)
    }
}
