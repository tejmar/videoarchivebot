package therealfarfetchd.videoarchivebot

import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.system.exitProcess

var running = true; private set

val dataDir = Paths.get("data")

fun main() {
  cmd

  runInputLoop()
  startWatcher()
  startArchiver()
  startMessageListener()

  Runtime.getRuntime().addShutdownHook(thread(start = false) { shutdown(wait = true) })

  Reddit.tryLogin()
}

fun runInputLoop() = task("Input") {
  while (true) {
    val line = readLine() ?: return@task

    lock {
      cmd.exec(line)
    }
  }
}

fun shutdown(wait: Boolean = false) {
  if (!running) return
  running = false

  val op = {
    Logger.info("Quitting...")

    stopTasks()

    Reddit.logout()

    cmd.save()
  }

  if (wait) op()
  else thread { op(); exitProcess(0) }
}