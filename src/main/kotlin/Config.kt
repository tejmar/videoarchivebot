package therealfarfetchd.videoarchivebot

import therealfarfetchd.videoarchivebot.FilterMode.BLOCK
import java.util.*

object Content : ValueStore {

  var defaultHandler: String = "default.sh"
  val customHandlers: MutableMap<String, String> = mutableMapOf()

  var siteFilterMode: FilterMode = BLOCK
  val siteFilter: MutableSet<String> = mutableSetOf()

  override fun init(ctx: ValueStoreInit) {
    ctx.addString("dl_handler_default", this::defaultHandler)
    ctx.addMap("dl_handler", this::customHandlers, { it }, { it }, { it.toLowerCase() }, { it })
    ctx.addCollection("dl_site_filter", this::siteFilter, { it }, { it })
    ctx.addCustom("dl_site_filter_mode", this::siteFilterMode, FilterMode.Companion::fromString, { it.name.toLowerCase(Locale.ROOT) })
  }

  override fun getDesc(prop: String) = when (prop) {
    "dl_handler_default" -> "The default/fallback download handler."
    "dl_handler" -> "A list of custom download handlers, by domain."
    "dl_site_filter" -> "The download sites that archiving videos should be allowed/blocked from, depending on the mode."
    "dl_site_filter_mode" -> "The filtering mode for download sites."
    else -> null
  }

  override fun getFile() = "content"

}

object IgnoredPosts : ValueStore {

  val ignore: MutableSet<String> = mutableSetOf()

  override fun init(ctx: ValueStoreInit) {
    ctx.addCollection("ignore", "unignoreall", "ignore_list", "unignore", this::ignore, { it }, { it })
  }

  override fun getDesc(prop: String) = when (prop) {
    "ignore" -> "Ignored posts. These can either be manually ignored or scanned and found to contain no videos"
    else -> null
  }

  override fun getFile() = "ignored_posts"

}

object LoginData : ValueStore {

  var username: String = ""
  var password: String = ""
  var id: String = ""
  var secret: String = ""

  var botProvider: String = "(unset, set with `bot_provider your_username`)"

  override fun init(ctx: ValueStoreInit) {
    ctx.addString("reddit_username", this::username)
    ctx.addString("reddit_password", this::password)
    ctx.addString("app_id", this::id)
    ctx.addString("app_secret", this::secret)
    ctx.addString("bot_provider", this::botProvider)
  }

  override fun getDesc(prop: String) = null

  override fun getFile() = "login"

}

object Subreddits : ValueStore {

  var watched: Set<String> = emptySet()
  var watchRate: Int = 10000

  var filterMode: FilterMode = BLOCK
  var filter: Set<String> = emptySet()

  var useFilterWorkaround = false

  override fun init(ctx: ValueStoreInit) {
    ctx.addStringSet("watch", "unwatchall", "watch_list", "unwatch", this::watched)
    ctx.addInt("watch_rate", this::watchRate, minValue = 1000)
    ctx.addStringSet("sub_filter", this::filter)
    ctx.addCustom("sub_filter_mode", this::filterMode, FilterMode.Companion::fromString, { it.name.toLowerCase(Locale.ROOT) })
    ctx.addBool("use_filter_workaround", this::useFilterWorkaround)
  }

  override fun getDesc(prop: String) = when (prop) {
    "watch" -> "The subreddits that should be automatically queried for new content."
    "watch_rate" -> "How often should be polled for new posts."
    "sub_filter" -> "The subreddits that archiving videos should be allowed/blocked from, depending on the mode."
    "sub_filter_mode" -> "The filtering mode for subreddits. Subreddits in the watch list are always allowed."
    "use_filter_workaround" -> "Enable region lock circumvention for certain subreddits"
    else -> null
  }

  override fun getFile() = "subreddits"

}

object Upload : ValueStore {

  val cachedLinks: MutableMap<String, String> = mutableMapOf()
  val linkTime: MutableMap<String, Long> = mutableMapOf()
  var uploader = "default.sh"

  override fun init(ctx: ValueStoreInit) {
    ctx.addMap("upload_link", this::cachedLinks, { it }, { it }, { it }, { it })
    ctx.addMap("upload_link_timeout", this::linkTime, { it }, { it.toLong() }, { it }, { it.toString() })
    ctx.addString("upload_handler", this::uploader)
  }

  override fun getFile() = "upload"

}

object State : ValueStore {

  var watchEnabled: Boolean = true
  var debug = 0

  override fun init(ctx: ValueStoreInit) {
    ctx.addBool("watch_enable", this::watchEnabled)
    ctx.addInt("debug", this::debug)
  }

  override fun getDesc(prop: String) = when (prop) {
    "watch_enable" -> "Watch selected subreddits for new content."
    "debug" -> "Debug output level for stdout."
    else -> null
  }

}

enum class FilterMode {
  ALLOW,
  BLOCK,
  ;

  companion object {
    fun fromString(s: String): FilterMode =
      FilterMode.values().find { it.name.equals(s, ignoreCase = true) } ?: BLOCK
  }
}