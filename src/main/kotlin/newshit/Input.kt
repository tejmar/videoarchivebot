package therealfarfetchd.videoarchivebot.newshit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

fun CoroutineScope.cliInput(channel: SendChannel<String>) = launch {
  val queue = ConcurrentLinkedQueue<String>()

  val t = thread(isDaemon = true, name = "Input Thread") {
    while (true) {
      val s = readLine() ?: break
      queue += s
    }
  }

  launch {
    loop@ while (true) {
      when (val s = queue.poll()) {
        null -> {
          if (!t.isAlive) break@loop
          else delay(100L)
        }
        else -> channel.send(s)
      }
    }
    channel.close()
  }.invokeOnCompletion { t.stop() }
}