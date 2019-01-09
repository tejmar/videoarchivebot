package therealfarfetchd.videoarchivebot

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import therealfarfetchd.videoarchivebot.newshit.cliInput
import therealfarfetchd.videoarchivebot.newshit.commandProcessor
import therealfarfetchd.videoarchivebot.newshit.downloader
import therealfarfetchd.videoarchivebot.newshit.redditListener
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.system.exitProcess

var running = true; private set

val dataDir = Paths.get("data")

fun main() = runBlocking {
  cmd

  GlobalScope.launch {
    val cmdProc = commandProcessor()
    val input = cliInput(cmdProc)

    val pms = redditListener()
    val downloader = downloader()


  }

  startWatcher()
  startArchiver()
  startMessageListener()

  Runtime.getRuntime().addShutdownHook(thread(start = false) { shutdown(wait = true) })

  Reddit.tryLogin()
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