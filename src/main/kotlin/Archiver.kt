package therealfarfetchd.videoarchivebot

import net.dean.jraw.models.Submission
import therealfarfetchd.videoarchivebot.ArchivalResult.AlreadyArchived
import therealfarfetchd.videoarchivebot.ArchivalResult.ArchiveError
import therealfarfetchd.videoarchivebot.ArchivalResult.Archived
import therealfarfetchd.videoarchivebot.ArchivalResult.FilteredSub
import therealfarfetchd.videoarchivebot.ArchivalResult.NoDownload
import therealfarfetchd.videoarchivebot.ArchivalResult.UnknownError
import therealfarfetchd.videoarchivebot.ArchiveDataError.InvalidArchive
import therealfarfetchd.videoarchivebot.ArchiveDataError.MissingArchive
import therealfarfetchd.videoarchivebot.FilterMode.ALLOW
import therealfarfetchd.videoarchivebot.LogType.ARCH
import therealfarfetchd.videoarchivebot.Result.Err
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

private val queue = ConcurrentLinkedQueue<Pair<Submission, (ArchivalResult) -> Unit>>()

private var fileCounter = 0u

val archiveDir = dataDir.resolve("archive")
val dlHandlerDir = dataDir.resolve("dl")
val workDir = dataDir.resolve("work")

fun startArchiver() = task("Archiver") {
  lock {
    try {
      if (Files.exists(workDir)) {
        Files.walkFileTree(workDir, object : SimpleFileVisitor<Path>() {
          override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return CONTINUE
          }

          override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            Files.delete(dir)
            return CONTINUE
          }
        })
      }
    } catch (e: IOException) {
      e.printStackTrace()
    }
  }

  while (true) {
    try {
      val entry = queue.poll()

      if (entry == null) {
        sleep(5000)
        continue
      }

      val (post, callback) = entry

      lock {
        if (isArchived(post)) {
          callback(AlreadyArchived)
          return@lock
        }

        if (!canDownload(post, callback)) {
          return@lock
        }

        Logger.log(ARCH, "[%d left] Archiving %s (%s)", queue.size, post.id, post.title)

        try {
          val result = download(post)
          if (result) callback(Archived)
          else callback(ArchiveError)
        } catch (e: Exception) {
          callback(UnknownError)
          throw e
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}

enum class ArchivalResult(val success: Boolean) {
  AlreadyArchived(true),
  Archived(true),
  NoDownload(false),
  FilteredSub(false),
  ArchiveError(false),
  UnknownError(false),
}

fun scheduleArchival(post: Submission, callback: (ArchivalResult) -> Unit = {}) {
  if (isArchived(post)) {
    callback(AlreadyArchived)
    return
  }

  queue += post to { result ->
    try {
      callback(result)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  Logger.log(ARCH, "[%d left] Scheduling archival of %s (%s)", queue.size, post.id, post.title)
}

private fun ignorePost(post: Submission) {
  IgnoredPosts.ignore += post.id
}

private fun download(post: Submission): Boolean {
  val handler = getDownloadHandler(post.domain)
  var dlHandler = getDLHandlerPath(handler)
  if (!Files.exists(dlHandler)) {
    Logger.warn("Nonexistent download handler %s for domain %s. Using default handler.", handler, post.domain)
    dlHandler = getDLHandlerPath(Content.defaultHandler)
    if (!Files.exists(dlHandler)) {
      Logger.err("Nonexistent default download handler %s for domain %s. Aborting.", handler, post.domain)
      return false
    }
  }

  val i = fileCounter++
  val video = workDir.resolve("dl_$i")
  Files.createDirectories(video.parent)

  val dl = exec(
    dlHandler.toAbsolutePath().toFile().toString(),
    post.url,
    video.toAbsolutePath().toFile().toString()
  )

  if (dl == null) {
    Files.deleteIfExists(video)
    ignorePost(post)
    return false
  }

  val t = thread(isDaemon = true) {
    // download locks up sometimes, so this is what we'll have to do I guess...
    sleep(60 * 1000L)
    dl.kill()
    Logger.err("Timed out (60 seconds) while downloading file '%s'", post.url)
  }

  dl.waitFor()
  t.stop()

  if (dl.exitCode != 0 || !Files.exists(video)) {
    Logger.err("Failed to download file '%s'.", post.url)
    Files.deleteIfExists(video)
    ignorePost(post)
    return false
  }

  val converted = video.resolveSibling("dl_$i.mp4")

  val ffmpeg = execSimple(
    "/usr/bin/env", "ffmpeg", "-i",
    video.toAbsolutePath().toFile().toString(),
    converted.toAbsolutePath().toFile().toString()
  )

  if (ffmpeg != 0 || !Files.exists(converted)) {
    Logger.err("Failed to convert file '%s' to MP4.", post.url)
    Files.deleteIfExists(video)
    Files.deleteIfExists(converted)
    ignorePost(post)
    return false
  }

  archive(post, converted)
  Files.delete(video)
  Files.delete(converted)

  return true
}

private fun archive(post: Submission, video: Path) {
  val md5 = getMD5(video)!!
  val videoPath = archiveDir.resolve("objects").resolve(md5.substring(0, 2)).resolve(md5)
  Files.createDirectories(videoPath.parent)

  if (!Files.exists(videoPath))
    Files.copy(video, videoPath)

  val postDir = archiveDir.resolve("by-id").resolve(post.id)
  Files.createDirectories(postDir)
  Files.createSymbolicLink(postDir.resolve("video.mp4"), postDir.relativize(videoPath))
  Files.writeString(postDir.resolve("post.cfg"),
    """; Post: ${post.title}
      |; Submitted by ${post.author}
      |; Archived at ${Instant.now().format()}
      |
      |post_title ${esc(post.title)}
      |post_author ${esc(post.author)}
      |post_subreddit ${post.subreddit}
      |${post.linkFlairCssClass?.let { "post_flair ${esc(it)}" }.orEmpty()}
      |
      |post_media video.mp4
      |post_archive_time ${utime()}
      |post_media_url ${esc(post.url)}
      |post_id ${post.id}
    """.trimMargin())

  val subDir = archiveDir.resolve("r").resolve(post.subreddit).resolve(post.id)
  Files.createDirectories(subDir.parent)
  Files.createSymbolicLink(subDir, subDir.parent.relativize(postDir))
}

private fun isArchived(post: Submission): Boolean {
  return Files.exists(archiveDir.resolve("by-id").resolve(post.id))
}

private fun canDownload(post: Submission, callback: (ArchivalResult) -> Unit): Boolean {
  if (post.id in IgnoredPosts.ignore) {
    callback(NoDownload)
    return false
  }

  if (post.subreddit !in Subreddits.watched && (post.subreddit in Subreddits.filter) == (Subreddits.filterMode == FilterMode.ALLOW)) {
    callback(FilteredSub)
    return false
  }

  if (post.isSelfPost) {
    callback(NoDownload)
    return false
  } // TODO check for link(s) inside the post text?

  return isDomainAllowed(post.domain)
}

private fun isDomainAllowed(d: String) =
  if (Content.siteFilterMode == ALLOW) getDomainParts(d).any { Content.siteFilter.contains(d) }
  else getDomainParts(d).none { Content.siteFilter.contains(d) }

private fun getDomainParts(domain: String) = sequence {
  var domainPart = domain
  while (true) {
    yield(domainPart)
    val next = domainPart.indexOf('.')
    if (next == -1) break
    domainPart = domainPart.drop(next + 1)
  }
}

private fun getDownloadHandler(domain: String) =
  getDomainParts(domain)
    .mapNotNull { Content.customHandlers[it] }
    .firstOrNull()
  ?: Content.defaultHandler

private fun getDLHandlerPath(handler: String): Path = dlHandlerDir.resolve(handler)

fun getArchiveData(postID: String): Result<ArchivedPost, ArchiveDataError> {
  val data = archiveDir.resolve("by-id").resolve(postID).resolve("post.cfg")
  if (!Files.exists(data)) return Err(MissingArchive)

  ArchivedPostHolder.clear()
  cmd.fromFile(data)
  return Result.from(ArchivedPostHolder.create()) { InvalidArchive }
}

enum class ArchiveDataError {
  MissingArchive,
  InvalidArchive,
}

object ArchivedPostHolder : ValueStore {

  var title = ""
  var author = ""
  var subreddit = ""
  var flair = ""

  var fileName = "video.mp4"
  var archiveTime = 0L
  var url = ""
  var id = ""

  fun clear() {
    title = ""
    author = ""
    subreddit = ""
    flair = ""
    fileName = "video.mp4"
    archiveTime = 0L
    url = ""
    id = ""
  }

  fun create() = try {
    ArchivedPost(title, author, subreddit, flair.takeIf { it.isNotEmpty() }, fileName, archiveTime, URL(url), id)
  } catch (e: MalformedURLException) {
    null
  }

  override fun init(ctx: ValueStoreInit) {
    ctx.addString("post_title", this::title)
    ctx.addString("post_author", this::author)
    ctx.addString("post_subreddit", this::subreddit)
    ctx.addString("post_flair", this::flair)
    ctx.addString("post_media", this::fileName)
    ctx.addLong("post_archive_time", this::archiveTime)
    ctx.addString("post_media_url", this::url)
    ctx.addString("post_id", this::id)
  }

  override fun getDesc(prop: String) = "Internal"

}

data class ArchivedPost(
  val title: String,
  val author: String,
  val subreddit: String,
  val flair: String?,
  val fileName: String,
  val archiveTime: Long,
  val url: URL,
  val id: String
)