package therealfarfetchd.videoarchivebot

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.locks.Lock
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

fun sleep(ms: Long) = Thread.sleep(ms)
fun utimem() = System.currentTimeMillis()
fun utime() = utimem() / 1000

var <T> KMutableProperty0<T>.value: T
  inline get() = get()
  inline set(value) = set(value)

val <T> KProperty0<T>.value: T
  inline get() = get()

fun getMD5(f: Path): String? {
  return try {
    val md = MessageDigest.getInstance("SHA-1")
    val data = ByteArray(8192)
    val istr = Files.newInputStream(f)
    DigestInputStream(istr, md).use { while (it.read(data) != -1); }
    md.digest().joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
  } catch (e: IOException) {
    null
  }
}

fun esc(s: String): String {
  var s = s
  s = s.replace(Regex("""["\\]"""), """\\$0""")
  if (" ;".any { it in s }) {
    s = "\"$s\""
  }

  return s
}

inline operator fun <R> Lock.invoke(op: () -> R): R {
  this.lock()
  try {
    return op()
  } finally {
    this.unlock()
  }
}

fun eprintln(a: Any?) = System.err.println(a)
fun eprint(a: Any?) = System.err.print(a)