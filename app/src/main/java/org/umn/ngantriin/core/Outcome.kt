package org.umn.ngantriin.core

/**
 * Hasil yang membawa [AppError] bertipe, bukan Throwable, jadi lapisan
 * UI tidak perlu menafsirkan exception.
 */
sealed interface Outcome<out T> {
    data class Success<out T>(val data: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val dataOrNull: T? get() = (this as? Success)?.data
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
