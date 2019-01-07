package therealfarfetchd.videoarchivebot

import net.dean.jraw.models.SubredditSort.NEW
import net.dean.jraw.pagination.BackoffStrategy

private val watchTasks = mutableMapOf<String, Task>()

fun startWatcher() = task("Watcher Manager") {
  while (true) {
    while (Reddit.isLoggedIn() && State.watchEnabled) {
      for (sub in Subreddits.watched - watchTasks.keys) {
        lock { watchTasks[sub] = watch(sub) }
      }

      for (sub in watchTasks.keys - Subreddits.watched) {
        lock {
          watchTasks[sub]?.stop()
          watchTasks.remove(sub)
        }
      }
      sleep(2000)
    }
    killWatchThreads()
    sleep(2000)
  }
}

private fun killWatchThreads() {
  watchTasks.values.forEach { it.stop() }
  watchTasks.clear()
}

private fun watch(sub: String) = task("Watching $sub") {
  while (true) {
    try {
      Reddit {
        val stream = it.subreddit(sub).posts().sorting(NEW).build().stream(ConfigBackoffStrategy)
        for (post in stream) {
          lock {
            scheduleArchival(post)
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    sleep(2000)
  }
}

private object ConfigBackoffStrategy : BackoffStrategy {

  override fun delayRequest(newItems: Int, totalItems: Int): Long = Subreddits.watchRate.toLong()

}