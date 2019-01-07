package therealfarfetchd.videoarchivebot

import net.dean.jraw.http.BasicAuthData
import net.dean.jraw.http.HttpRequest
import net.dean.jraw.http.HttpResponse
import net.dean.jraw.http.NetworkAdapter
import net.dean.jraw.http.UserAgent
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * [NetworkAdapter] implementation backed by Square's fantastic [OkHttp](https://square.github.io/okhttp/)
 * Modified [net.dean.jraw.http.OkHttpNetworkAdapter] to circumvent reddit's stupid region lock for certain subreddits
 */
class FixedOkHttpNetworkAdapter @JvmOverloads constructor(
  /** The value of the User-Agent header sent with every request */
  override var userAgent: UserAgent,
  /** A customized OkHttpClient instance. Defaults to a default client. */
  private val http: OkHttpClient = OkHttpClient()
) : NetworkAdapter {

  override fun execute(r: HttpRequest): HttpResponse {
    return HttpResponse(createCall(r).execute())
  }

  override fun connect(url: String, listener: WebSocketListener): WebSocket {
    val client = OkHttpClient()

    val ws = client.newWebSocket(Request.Builder()
      .get()
      .url(url.replace("reddit.com/", "reddit.com//"))
      .build(), listener)

    // Shutdown the ExecutorService so this program can terminate normally
    client.dispatcher().executorService().shutdown()

    return ws
  }

  private fun createCall(r: HttpRequest): Call =
    (if (r.basicAuth != null) createAuthenticatedClient(r.basicAuth!!) else http).newCall(compileRequest(r))

  private fun createAuthenticatedClient(data: BasicAuthData): OkHttpClient =
    http.newBuilder().authenticator(BasicAuthenticator(data)).build()

  private fun compileRequest(r: HttpRequest): Request =
    Request.Builder()
      .headers(r.headers.newBuilder().set("User-Agent", userAgent.value).build())
      .url(r.url.replace("reddit.com/", "reddit.com//"))
      .method(r.method, r.body)
      .build()
}

/**
 * Copy of [net.dean.jraw.http.BasicAuthenticator] because that one's internal
 */
private class BasicAuthenticator(private val data: BasicAuthData): Authenticator {
  override fun authenticate(route: Route?, response: Response): Request? {
    val credential = Credentials.basic(data.username, data.password)
    val header = if (response.code() == 407) "Proxy-Authorization" else "Authorization"
    return response.request().newBuilder().header(header, credential).build()
  }
}
