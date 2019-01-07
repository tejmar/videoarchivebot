package therealfarfetchd.videoarchivebot

sealed class Result<out T : Any, out E : Any> {
  data class Ok<out T : Any>(val value: T) : Result<T, Nothing>() {
    override fun <R : Any> map(op: (T) -> R) = Ok(op(value))
    override fun <R : Any> mapErr(op: (Nothing) -> R) = this
    override fun ok() = value
    override fun err() = null
    override fun toString() = "Ok($value)"
  }

  data class Err<out E : Any>(val error: E) : Result<Nothing, E>() {
    override fun <R : Any> map(op: (Nothing) -> R) = this
    override fun <R : Any> mapErr(op: (E) -> R) = Err(op(error))
    override fun ok() = null
    override fun err() = error
    override fun toString() = "Err($error)"
  }

  abstract fun <R : Any> map(op: (T) -> R): Result<R, E>
  abstract fun <R : Any> mapErr(op: (E) -> R): Result<T, R>

  fun unwrap(): T = ok() ?: error("Can't unwrap $this!")
  fun unwrapErr(): E = err() ?: error("Can't unwrapErr $this!")

  abstract fun ok(): T?
  abstract fun err(): E?

  companion object {
    inline fun <T : Any, E : Any> from(value: T?, or: () -> E) = value?.let(::Ok) ?: Err(or())
    inline fun <T : Any, E : Any> fromErr(value: E?, or: () -> T) = value?.let(::Err) ?: Ok(or())
  }
}