package therealfarfetchd.videoarchivebot.newshit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import therealfarfetchd.videoarchivebot.Content
import therealfarfetchd.videoarchivebot.Result
import therealfarfetchd.videoarchivebot.Result.Err
import therealfarfetchd.videoarchivebot.Result.Ok
import therealfarfetchd.videoarchivebot.dlHandlerDir
import therealfarfetchd.videoarchivebot.exec
import therealfarfetchd.videoarchivebot.newshit.DownloaderError.ExecError
import therealfarfetchd.videoarchivebot.newshit.DownloaderError.MissingHandler
import therealfarfetchd.videoarchivebot.newshit.DownloaderError.NotExecutableHandler
import therealfarfetchd.videoarchivebot.newshit.DownloaderError.StartError
import therealfarfetchd.videoarchivebot.newshit.DownloaderError.TimeoutError
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

data class DownloaderRequest(val url: URL, val dir: Path, val response: CompletableDeferred<Result<List<Path>, DownloaderError>>)

sealed class DownloaderError {
  data class MissingHandler(val dlHandler: String) : DownloaderError()
  data class NotExecutableHandler(val dlHandler: String) : DownloaderError()
  data class StartError(val dlHandler: String) : DownloaderError()
  data class TimeoutError(val dlHandler: String, val processOutput: String, val timeout: Long) : DownloaderError()
  data class ExecError(val dlHandler: String, val processOutput: String, val errorCode: Int) : DownloaderError()
}

fun CoroutineScope.downloader(parallel: Int = 4) = actor<DownloaderRequest> {
  repeat(parallel) {
    launch {
      val thr = newSingleThreadContext("Process Wait Thread")
      for ((url, dir, response) in channel) {
        val handler = getDownloadHandler(url.host)
        val handlerPath = getDLHandlerPath(handler)

        if (!Files.exists(handlerPath)) {
          response.complete(Err(MissingHandler(handler)))
          continue
        }

        if (!Files.isExecutable(handlerPath)) {
          response.complete(Err(NotExecutableHandler(handler)))
          continue
        }

        val p = exec(handlerPath.toAbsolutePath().toString(), dir = dir)

        if (p == null) {
          response.complete(Err(StartError(handler)))
          continue
        }

        val timeout = Content.downloadTimeout
        val timedOut = withContext(thr) { p.waitFor(timeout * 1000) }

        val files = Files.walk(dir).asSequence().filter { Files.isRegularFile(it) }.toList()

        if (timedOut) {
          p.kill()
          response.complete(Err(TimeoutError(handler, p.all, timeout)))
          files.forEach(Files::delete)
          continue
        }

        if (p.exitCode != 0) {
          response.complete(Err(ExecError(handler, p.all, p.exitCode)))
          files.forEach(Files::delete)
          continue
        }

        response.complete(Ok(files))
      }
      thr.close()
    }
  }
}

private fun getDownloadHandler(domain: String) =
  getDomainParts(domain)
    .mapNotNull { Content.customHandlers[it] }
    .firstOrNull()
  ?: Content.defaultHandler

private fun getDLHandlerPath(handler: String): Path = dlHandlerDir.resolve(handler)

private fun getDomainParts(domain: String) = sequence {
  var domainPart = domain
  while (true) {
    yield(domainPart)
    val next = domainPart.indexOf('.')
    if (next == -1) break
    domainPart = domainPart.drop(next + 1)
  }
}