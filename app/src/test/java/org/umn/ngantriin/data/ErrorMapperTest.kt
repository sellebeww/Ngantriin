package org.umn.ngantriin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.ErrorMapper
import java.io.IOException
import java.net.UnknownHostException

/**
 * The SQL functions raise bare tokens; these are the translations the user
 * actually sees, so a rename on either side has to break a test here.
 */
class ErrorMapperTest {

    @Test
    fun `network failures map to the offline error`() {
        assertEquals(AppError.Network, ErrorMapper.map(UnknownHostException("no dns")))
        assertEquals(AppError.Network, ErrorMapper.map(IOException("socket closed")))
    }

    @Test
    fun `queue rules from the database reach the user as copy`() {
        assertEquals(AppError.AlreadyInQueue, ErrorMapper.map(Exception("ALREADY_IN_QUEUE")))
        assertEquals(AppError.QueueFull, ErrorMapper.map(Exception("QUEUE_FULL")))
        assertEquals(AppError.RestaurantClosed, ErrorMapper.map(Exception("RESTAURANT_CLOSED")))
        assertEquals(AppError.QueueNotCalled, ErrorMapper.map(Exception("QUEUE_NOT_CALLED")))
        assertEquals(AppError.QueueEmpty, ErrorMapper.map(Exception("QUEUE_EMPTY")))
        assertEquals(AppError.NotRestaurantStaff, ErrorMapper.map(Exception("NOT_RESTAURANT_STAFF")))
    }

    @Test
    fun `the unique index violation is read as an existing queue`() {
        val error = ErrorMapper.map(
            Exception("duplicate key value violates unique constraint " +
                "\"queues_one_active_per_user_idx\"")
        )
        assertEquals(AppError.AlreadyInQueue, error)
    }

    @Test
    fun `a missing check-in photo reaches the user as copy`() {
        assertEquals(AppError.PhotoRequired, ErrorMapper.map(Exception("PHOTO_REQUIRED")))
    }

    @Test
    fun `the post-cancel cooldown reaches the user as copy`() {
        assertEquals(AppError.JoinCooldown, ErrorMapper.map(Exception("JOIN_COOLDOWN")))
    }

    @Test
    fun `an expired token asks the user to sign in again`() {
        assertEquals(AppError.SessionExpired, ErrorMapper.map(Exception("JWT expired")))
        assertEquals(
            AppError.InvalidCredentials,
            ErrorMapper.map(Exception("Invalid login credentials"))
        )
    }

    @Test
    fun `an unrecognised failure still produces something showable`() {
        val error = ErrorMapper.map(Exception("teapot"))
        assertTrue(error is AppError.Server)
    }
}
