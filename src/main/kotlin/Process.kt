package therealfarfetchd.videoarchivebot

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.reflect.KMutableProperty0
import java.lang.Process as JProcess

class Process(private val p: JProcess) {
  fun dead() = !p.isAlive

  val exitCode by lazy { waitFor(); p.exitValue() }
  val errored by lazy { exitCode != 0 }

  private var snipOut: List<Pair<Long, String>> = emptyList()
  private var snipErr: List<Pair<Long, String>> = emptyList()

  private var ts1 = true
  private var ts2 = true

  val out: String
    get() = snipOut
      .joinToString("") { it.second }

  val err: String
    get() = snipErr
      .joinToString("") { it.second }

  val all: String
    get() = (snipOut + snipErr)
      .sortedBy { it.first }
      .joinToString("") { it.second }

  init {
    thread { scan(p.inputStream, ::snipOut, ::ts1) }
    thread { scan(p.errorStream, ::snipErr, ::ts2) }
  }

  fun put(s: String) {
    p.outputStream.write(s.toByteArray())
    p.outputStream.flush()
  }

  fun eof() {
    p.outputStream.close()
  }

  fun kill() {
    p.destroyForcibly()
  }

  private fun scan(str: InputStream, dest: KMutableProperty0<List<Pair<Long, String>>>, upd: KMutableProperty0<Boolean>) {
    val b = str.bufferedReader()
    val arr = CharArray(256)
    do {
      val stmp = System.nanoTime()
      val len = b.read(arr)
      if (len > 0)
        dest.value += stmp to String(arr, 0, len)
    } while (len != -1)
    upd.value = false
  }

  fun waitFor(): Process {
    p.waitFor()
    while (ts1 || ts2) sleep(250)
    return this
  }

  fun waitFor(ms: Long): Boolean {
    return p.waitFor(ms, TimeUnit.MILLISECONDS).also { if (it) while (ts1 || ts2) sleep(250) }
  }
}

fun exec(vararg args: String): Process? {
  return try {
    val proc = ProcessBuilder()
      .command(*args)
      .start()
    Process(proc)
  } catch (e: IOException) {
    null
  }
}

fun execSimple(vararg args: String): Int {
  return try {
    val proc = ProcessBuilder()
      .command(*args)
      .start()
    proc.waitFor()
  } catch (e: IOException) {
    126
  }
}

