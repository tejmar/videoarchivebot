package therealfarfetchd.videoarchivebot

import net.dean.jraw.RedditClient
import net.dean.jraw.http.OkHttpNetworkAdapter
import net.dean.jraw.http.UserAgent
import net.dean.jraw.models.Comment
import net.dean.jraw.models.Message
import net.dean.jraw.oauth.Credentials
import net.dean.jraw.oauth.OAuthHelper
import net.dean.jraw.references.CommentReference
import net.dean.jraw.references.SubmissionReference
import okhttp3.OkHttpClient

object Reddit {
  private var reddit: RedditClient? = null

  private val http by lazy { OkHttpClient() }

  operator fun <R> invoke(op: (RedditClient) -> R): R? {
    return reddit?.let(op)
  }

  // Use Reddit.invoke instead
  fun get(): RedditClient? = reddit

  fun tryLogin() {
    if (reddit == null && !LoginData.username.isEmpty() && !LoginData.password.isEmpty() && !LoginData.id.isEmpty() && !LoginData.secret.isEmpty()) {
      val creds = Credentials.script(LoginData.username, LoginData.password, LoginData.id, LoginData.secret)
      val ua = UserAgent("bot", "videoarchivebot", "1.0.0", "the_real_farfetchd")

      val adapter =
        if (Subreddits.useFilterWorkaround) FixedOkHttpNetworkAdapter(ua, http)
        else OkHttpNetworkAdapter(ua, http)

      val r = OAuthHelper.automatic(adapter, creds)
      r.logger = httpLogger

      reddit = r
    }

    Logger.info("login status: %s", reddit != null)
  }

  fun logout() {
    reddit = null
  }

  fun isLoggedIn() = reddit != null
}

fun Message.comment(): CommentReference? {
  if (!isComment) return null
  return Reddit.get()!!.comment(id)
}

fun CommentReference.inspect(): Comment {
  return Reddit.get()!!.lookup(fullName).first() as Comment
}

fun Comment.submission(): SubmissionReference {
  return Reddit.get()!!.submission(this.submissionFullName.substring(3))
}