package therealfarfetchd.videoarchivebot

import therealfarfetchd.videoarchivebot.Result.Err
import therealfarfetchd.videoarchivebot.Result.Ok
import therealfarfetchd.videoarchivebot.UploadError.ArchiveError
import therealfarfetchd.videoarchivebot.UploadError.InvalidURL
import therealfarfetchd.videoarchivebot.UploadError.MissingFile
import therealfarfetchd.videoarchivebot.UploadError.MissingUploadScript
import therealfarfetchd.videoarchivebot.UploadError.UploadScriptError
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

val uploadHandlerDir = dataDir.resolve("ul")

fun provideFile(postID: String): Result<URL, UploadError> {
  cleanLinks()

  Upload.cachedLinks[postID]?.also { return Ok(URL(it)) }

  return when(val d = getArchiveData(postID)) {
    is Ok -> {
      val video = archiveDir.resolve("by-id").resolve(postID).resolve(d.value.fileName)
      if (!Files.exists(video)) return Err(MissingFile)

      when (val result = upload(video)) {
        is Ok -> {
          Upload.cachedLinks[postID] = result.value.url.toString()

          if (result.value.duration > 0)
            Upload.linkTime[postID] = utime() + result.value.duration

          Ok(result.value.url)
        }
        is Err -> result
      }
    }
    is Err -> Err(ArchiveError(d.error))
  }
}

private fun upload(file: Path): Result<UploadResult, UploadError> {
  val uploader = uploadHandlerDir.resolve(Upload.uploader)
  if (!Files.exists(uploader)) return Err(MissingFile)

  val upload = exec(uploader.toAbsolutePath().toString(), file.toAbsolutePath().toString()) ?: return Err(MissingUploadScript)
  if (upload.exitCode != 0) return Err(UploadScriptError)

  UploadResultHolder.clear()

  val lines = upload.out.lines()
  lines.forEach { cmd.exec(it) }

  return Result.from(UploadResultHolder.create()) { InvalidURL }
}

private fun cleanLinks() {
  Upload.cachedLinks.keys
    .filter { Upload.linkTime[it] ?: 0L < utime() }
    .also { Upload.cachedLinks -= it; Upload.linkTime -= it }
}

object UploadResultHolder : ValueStore {

  var uploadURL = ""
  var uploadDuration = 0

  fun clear() {
    uploadURL = ""
    uploadDuration = 0
  }

  fun create() = try {
    UploadResult(URL(uploadURL), uploadDuration)
  } catch (e: MalformedURLException) {
    null
  }

  override fun init(ctx: ValueStoreInit) {
    ctx.addString("upload_url", this::uploadURL)
    ctx.addInt("upload_duration", this::uploadDuration)
  }

  override fun getDesc(prop: String) = "Internal"

}

data class UploadResult(val url: URL, val duration: Int)

sealed class UploadError {
  object MissingFile : UploadError()
  object MissingUploadScript : UploadError()
  object UploadScriptError : UploadError()
  object InvalidURL : UploadError()

  data class ArchiveError(val error: ArchiveDataError) : UploadError() {
    override fun toString() = "ArchiveError($error)"
  }

  override fun toString(): String = javaClass.simpleName
}