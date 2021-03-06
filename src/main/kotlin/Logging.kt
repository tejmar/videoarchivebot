package therealfarfetchd.videoarchivebot

import net.dean.jraw.http.LogAdapter
import net.dean.jraw.http.SimpleHttpLogger
import therealfarfetchd.videoarchivebot.LogType.ERR
import therealfarfetchd.videoarchivebot.LogType.HTTP
import therealfarfetchd.videoarchivebot.LogType.INFO
import therealfarfetchd.videoarchivebot.LogType.WARN
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

val httpLogger = SimpleHttpLogger(out = LogAdapter)

val logDir = dataDir.resolve("log")

private object LogAdapter : LogAdapter {

  override fun writeln(data: String) {
    Logger.log(HTTP, data)
  }

}

object Logger {

  val logfile = logDir.resolve("bot.log")
  val maxFiles = 5

  private val lock = ReentrantLock(false)

  init {
    if (Files.exists(logfile)) {
      move(0)
      Files.move(logfile, logfile.resolveSibling("${logfile.fileName}.0"))
    }
  }

  fun info(fmt: String, vararg args: Any?) = log(INFO, fmt, *args)

  fun warn(fmt: String, vararg args: Any?) = log(WARN, fmt, *args)

  fun err(fmt: String, vararg args: Any?) = log(ERR, fmt, *args)

  fun log(type: LogType, fmt: String, vararg args: Any?) {
    lock {
      val prefix = getPrefix(type)
      fmt.format(*args).lineSequence().forEach {
        if (type.level <= State.debug) type.output(it)
        val line = prefix + it
        Files.createDirectories(logfile.parent)
        Files.writeString(logfile, line + '\n', StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      }
    }
  }

  private fun move(i: Int) {
    val l1 = logfile.resolveSibling("${logfile.fileName}.$i")
    if (Files.exists(l1)) {
      val l2 = logfile.resolveSibling("${logfile.fileName}.${i + 1}")
      if (Files.exists(l2)) {
        move(i + 1)
      }

      if (i >= maxFiles) {
        Files.delete(l1)
      } else {
        Files.move(l1, l2)
      }
    }
  }

  @Suppress("LocalVariableName")
  private fun getPrefix(type: LogType): String {
    val now = Instant.now().format()

    val lt = type.name.padEnd(4)

    return "[$now|$lt] "
  }

}

enum class LogType(val level: Int, val output: (Any?) -> Unit = ::println) {
  INFO(0),
  WARN(0),
  ERR(0, output = ::eprintln),
  CMD(1),
  TASK(1),
  ARCH(2),
  HTTP(3),
}