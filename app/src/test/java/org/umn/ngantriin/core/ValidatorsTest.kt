package org.umn.ngantriin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `email must look like an email`() {
        assertNull(Validators.emailError("student@umn.ac.id"))
        assertNull(Validators.emailError("  first.last+tag@example.co  "))
        assertNotNull(Validators.emailError(""))
        assertNotNull(Validators.emailError("not-an-email"))
        assertNotNull(Validators.emailError("missing@tld"))
    }

    @Test
    fun `password has a minimum length`() {
        assertNull(Validators.passwordError("longenough"))
        assertNotNull(Validators.passwordError("short"))
        assertNotNull(Validators.passwordError(""))
    }

    @Test
    fun `confirmation must match`() {
        assertNull(Validators.confirmPasswordError("password1", "password1"))
        assertNotNull(Validators.confirmPasswordError("password1", "password2"))
        assertNotNull(Validators.confirmPasswordError("password1", ""))
    }

    @Test
    fun `name rejects blanks and single characters`() {
        assertNull(Validators.nameError("Rina"))
        assertNotNull(Validators.nameError(" "))
        assertNotNull(Validators.nameError("R"))
    }

    @Test
    fun `password rule matches the documented minimum`() {
        assertEquals(8, Constants.MIN_PASSWORD_LENGTH)
    }
}
