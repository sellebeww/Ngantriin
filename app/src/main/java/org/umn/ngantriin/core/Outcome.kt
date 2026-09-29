package org.umn.ngantriin.core

/**
 * A result that carries a typed [AppError] instead of a Throwable, so the
 * UI layer never has to interpret exceptions.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val data: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val dataOrNull: T? get() = (this as? Success)?.data
    val errorOrNull: AppError? get() = (this as? Failure)?.error
    val isSuccess: Boolean get() = this is Success
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Success) action(data)
}

inline fun <T> Outcome<T>.onFailure(action: (AppError) -> Unit): Outcome<T> = apply {
    if (this is Outcome.Failure) action(error)
}

fun <T> T.asSuccess(): Outcome<T> = Outcome.Success(this)
fun AppError.asFailure(): Outcome<Nothing> = Outcome.Failure(this)
