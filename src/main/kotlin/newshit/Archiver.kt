package therealfarfetchd.videoarchivebot.newshit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import net.dean.jraw.models.Submission
import therealfarfetchd.videoarchivebot.Reddit
import therealfarfetchd.videoarchivebot.Result
import therealfarfetchd.videoarchivebot.Result.Ok

interface PostData {
  fun getId(): String

  fun getSubmission(): Submission

  data class ById(private val id: String) : PostData {
    private var submission: Submission? = null

    override fun getId() = id

    override fun getSubmission() = submission ?: run {
      val s = Reddit.get()!!.submission(id).inspect() // FIXME this will crash and burn when not logged in
      submission = s
      s
    }
  }

  data class BySubmission(private val submission: Submission) : PostData {
    override fun getId() = submission.id

    override fun getSubmission() = submission
  }
}

data class ArchiverRequest(val data: PostData, val response: CompletableDeferred<Result<Unit, Unit>>)

fun CoroutineScope.archiver(downloader: SendChannel<DownloaderRequest>) = actor<ArchiverRequest> {
  val inProgress = mutableMapOf<String, Deferred<Result<Unit, Unit>>>()
  for (msg in channel) {
    val postId = msg.data.getId()
    val d = inProgress.getOrPut(postId) {
      async<Result<Unit, Unit>> {
        Ok(Unit)
      }.apply { invokeOnCompletion { inProgress -= postId } }
    }
    d.invokeOnCompletion { msg.response.complete(d.getCompleted()) }
  }
}