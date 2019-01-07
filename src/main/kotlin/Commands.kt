package therealfarfetchd.videoarchivebot

import net.dean.jraw.models.SubredditSort.NEW

val configDir = dataDir.resolve("cfg")

val cmd = CommandSys(configDir,
  ArchivedPostHolder,
  Content,
  IgnoredPosts,
  Upload,
  UploadResultHolder,
  LoginData,
  Subreddits,
  State
).apply {
  addCommand("connect") {
    Reddit.tryLogin()
  }

  addCommand("disconnect") {
    Reddit.logout()
  }

  addCommand("reconnect") {
    Reddit.logout()
    Reddit.tryLogin()
  }

  addCommand("tasks") {
    val tasks = getTasks()
    Logger.info("Tasks (%d):", tasks.size)
    for (task in tasks) {
      Logger.info(" - %s", task.name)
    }
  }

  addCommand("quit") {
    shutdown()
  }

  addCommand("test") {
    Logger.info("%s", provideFile(it[0]))
  }

  addCommand("lookup") {
    Reddit { r ->
      Logger.info("%s", r.lookup(it[0]).toList())
      Unit
    }
  }

  addCommand("retry_ignored") {
    Reddit { r ->
      val posts = IgnoredPosts.ignore.toList()
      IgnoredPosts.ignore.clear()
      posts.map { r.submission(it).inspect() }.forEach(::scheduleArchival)
    }
  }

  addCommand("archive_sub_full") {
    Reddit { r ->
      val sub = r.subreddit(it[0])
      val posts = sub.posts().sorting(NEW).limit(100).build()
      task("Fully archiving ${sub.subreddit}") {
        for (page in posts) {
          for (post in page) {
            scheduleArchival(post)
          }
        }
      }
    }
  }
}