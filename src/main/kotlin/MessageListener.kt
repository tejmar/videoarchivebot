package therealfarfetchd.videoarchivebot

import net.dean.jraw.models.Message
import net.dean.jraw.references.SubmissionReference
import therealfarfetchd.videoarchivebot.ArchivalResult.AlreadyArchived
import therealfarfetchd.videoarchivebot.ArchivalResult.ArchiveError
import therealfarfetchd.videoarchivebot.ArchivalResult.Archived
import therealfarfetchd.videoarchivebot.ArchivalResult.FilteredSub
import therealfarfetchd.videoarchivebot.ArchivalResult.NoDownload
import therealfarfetchd.videoarchivebot.ArchivalResult.UnknownError
import therealfarfetchd.videoarchivebot.ArchiveDataError.InvalidArchive
import therealfarfetchd.videoarchivebot.ArchiveDataError.MissingArchive
import therealfarfetchd.videoarchivebot.Result.Err
import therealfarfetchd.videoarchivebot.Result.Ok
import java.time.Instant

val postRefRegex = Regex("(https?://(\\w+\\.)?reddit\\.com/+(r/+\\w+/+)?|/?r/+\\w+/+)(comments/+)?(\\w+)(/+\\S*)?")

fun startMessageListener() = task("Message Listener") {
  while (true) {
    lock {
      try {
        Reddit {
          val list = it.me().inbox().iterate("unread").build().accumulateMerged(5)
          for (message in list) {
            it.me().inbox().markRead(true, message.fullName)

            val posts = getPosts(message).distinctBy { it.id }
            if (posts.isNotEmpty()) {
              val result = mutableMapOf<String, ArchivalResult>()

              posts.forEach { post ->
                scheduleArchival(post.inspect()) { r ->
                  result[post.id] = r
                }
              }

              while (result.size != posts.size) sleep(500)

              val texts = posts.joinToString("\n____________________\n") { post ->
                when (val data = getArchiveData(post.id)) {
                  is Ok -> {
                    val v = data.value
                    when (val url = provideFile(post.id)) {
                      is Ok -> {
                        val expiresIn = Upload.linkTime[post.id]?.let { it - utime() }
                        val expiresInText = expiresIn?.let { " ^(link expires in ${formatDurationRough(it)})" }.orEmpty()

                        """Post: [${v.title}](https://reddit.com/${v.id}) in /r/${v.subreddit}
                          |Author: ${v.author}
                          |**Archived video**: ${url.value}$expiresInText
                          |Original link: ${v.url}
                          |Archived at ${Instant.ofEpochSecond(v.archiveTime).formatUTC()} (UTC)
                        """.trimMargin()
                      }
                      is Err -> {
                        """Post: "${v.title}" from /r/${v.subreddit}
                          |Author: ${v.author}
                          |Original link: ${v.url}
                          |Archived at ${v.archiveTime} (showing an actual date here is WIP, haha)
                          |**Could not upload archived content. Contact /u/${LoginData.botProvider} to make him fix this**
                        """.trimMargin()
                      }
                    }
                  }
                  is Err -> {
                    val error = when (result.getValue(post.id)) {
                      NoDownload -> "No media found in post/failed to archive."
                      FilteredSub -> "Subreddit containing post is not allowed to be archived."
                      ArchiveError, UnknownError -> "Failed to archive post."
                      AlreadyArchived, Archived -> when(data.error) {
                        MissingArchive -> "Missing archive data."
                        InvalidArchive -> "Invalid archive data."
                      }
                    }

                    """Post: https://reddit.com/${post.id}
                      |**$error Contact /u/${LoginData.botProvider} if you think this is incorrect.**
                    """.trimMargin()
                  }
                }
              }.replace("\n", "  \n")

              message.reply(
                """Results (${posts.size} referenced posts):
                |
                |____________________
                |
                |$texts
                |
                |____________________
                |
                |^([I am a bot. ~~If~~ When I act up, yell at ${LoginData.botProvider} | v$version])
              """.trimMargin())
            }
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    sleep(10000)
  }
}

private fun getPosts(m: Message): List<SubmissionReference> {
  getPostsFromBody(m).also { if (it.isNotEmpty()) return it }

  return listOfNotNull(m.comment()?.inspect()?.submission())
}

private fun getPostsFromBody(m: Message): List<SubmissionReference> {
  val text = m.body
  return Reddit { r -> postRefRegex.findAll(text).map { it.groups[5]!!.value }.map { r.submission(it) }.toList() }.orEmpty()
}

private fun Message.reply(text: String) {
  if (this.fullName.startsWith("t3_")) {
    // comment
    comment()!!.reply(text)
  } else if (this.fullName.startsWith("t4_")) {
    // PM
    Reddit.get()!!.me().inbox().replyTo(this.fullName, text)
  }
}