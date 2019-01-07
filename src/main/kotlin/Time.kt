package therealfarfetchd.videoarchivebot

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

private val formatter = DateTimeFormatter
  .ofPattern("yyyy-MM-dd HH:mm:ss")
  .withLocale(Locale.ROOT)
  .withZone(ZoneOffset.UTC)

private val formatterLocal = DateTimeFormatter
  .ofPattern("yyyy-MM-dd HH:mm:ss")
  .withLocale(Locale.ROOT)
  .withZone(ZoneId.systemDefault())

fun sleep(ms: Long) = Thread.sleep(ms)
fun utime() = Instant.now().epochSecond

fun Instant.formatUTC(): String = formatter.format(this)
fun Instant.format(): String = formatterLocal.format(this)

@Suppress("NAME_SHADOWING")
fun formatDurationRough(secsIn: Long): String {
  var counter = secsIn
  val secs = counter % 60
  counter /= 60
  val mins = counter % 60
  counter /= 60
  val hours = counter % 24
  counter /= 24
  val days = counter

  if (days > 0) return "$days days"
  if (hours > 0) return "$hours hours"
  if (mins > 0) return "$mins minutes"
  return "$secs seconds"
}