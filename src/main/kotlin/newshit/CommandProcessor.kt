package therealfarfetchd.videoarchivebot.newshit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.actor
import therealfarfetchd.videoarchivebot.cmd

fun CoroutineScope.commandProcessor() = actor<String> {
  for (msg in channel) {
    cmd.exec(msg)
  }
}
