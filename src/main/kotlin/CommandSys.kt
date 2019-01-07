package therealfarfetchd.videoarchivebot

import therealfarfetchd.videoarchivebot.LogType.CMD
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

class CommandSys(val configDir: Path, vararg val stores: ValueStore) {

  private val commands: MutableMap<String, (List<String>) -> Unit> = mutableMapOf()

  private val cmdToStoreMap: MutableMap<String, ValueStore> = mutableMapOf()
  private val storeToCmdsMap: MutableMap<ValueStore, MutableList<String>> = mutableMapOf()
  private val serializers: MutableMap<String, () -> List<String>> = mutableMapOf()

  private val lock = ReentrantLock(true)

  init {
    stores.forEach { it.init(ValueStoreInitImpl(this, it)) }

    addCommand("save") { save() }

    addCommand("list") {
      val maxCommandLen = commands.keys.map { it.length }.max() ?: 0
      for (v in commands.keys.sorted()) {
        val desc = getDesc(v)
        val text = if (desc != null) v.padEnd(maxCommandLen + 1) + desc else v
        Logger.info("%s", text)
      }
    }

    addCommand("exec") {
      if (it.isNotEmpty()) {
        load(it[0])
      }
    }

    addCommand("echo") {
      Logger.info("%s", it.joinToString(" "))
    }

    load()
  }

  fun save() {
    val now = Instant.now()
    for (store in stores) {
      store.getFile()?.also { f ->
        val path = configDir.resolve("$f.cfg")
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { w ->
          w.appendln(
            """; File: $f.cfg
              |; Generated: $now
              |
            """.trimMargin())

          storeToCmdsMap[store]?.let {
            for (prop in it.sorted()) {
              val desc = store.getDesc(prop)
              if (desc != null) w.appendln(desc.prependIndent("; "))
              serializers[prop]?.invoke()?.forEach { w.appendln(it) }
              if (desc != null) w.appendln()
            }
          }
        }
      }
    }
  }

  fun load() {
    Files.createDirectories(configDir)
    stores.forEach { it.getFile()?.also { load(it) } }

    load("autoexec")
  }

  fun fromFile(f: Path) {
    try {
      Files.readAllLines(f).forEach(::exec)
    } catch (e: IOException) {
    }
  }

  fun load(cfg: String) {
    fromFile(configDir.resolve("$cfg.cfg"))
  }

  fun call(prop: String, args: List<String>) {
    lock {
      try {
        commands[prop]?.invoke(args.toList())
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  fun exec(line: String) {
    if (State.debug > 0) {
      Logger.log(CMD, "] %s", line)
    }

    val tokens = tokenize2(line)
    if (tokens.isEmpty()) return

    call(tokens[0], tokens.drop(1))
  }

  fun addCommand(cmd: String, op: (List<String>) -> Unit) {
    require(!commands.contains(cmd)) { "Tried to register duplicate command $cmd!" }
    commands[cmd] = op
  }

  fun linkStore(cmd: String, store: ValueStore) {
    cmdToStoreMap[cmd]?.also { storeToCmdsMap[it]?.remove(cmd) }
    cmdToStoreMap[cmd] = store
    storeToCmdsMap.computeIfAbsent(store) { mutableListOf() } += cmd
  }

  fun setSerializer(cmd: String, serializer: () -> List<String>) {
    serializers[cmd] = serializer
  }

  fun getDesc(prop: String): String? = cmdToStoreMap[prop]?.getDesc(prop)

}

private class ValueStoreInitImpl(val sys: CommandSys, val store: ValueStore) : ValueStoreInit {

  override fun <T> addCustom(prop: String, field: KMutableProperty0<T>, into: (String) -> T, from: (T) -> String) {
    sys.addCommand(prop) {
      if (it.isEmpty()) {
        Logger.info("%s = %s", prop, from(field.get()))
      } else {
        field.set(into(it[0]))
      }
    }

    sys.linkStore(prop, store)
    sys.setSerializer(prop) {
      listOf("$prop ${esc(from(field.get()))}")
    }
  }

  private fun <C : Collection<T>, T> addROCollection(
    addCommand: String, clearCommand: String, listCommand: String, removeCommand: String,
    field: KMutableProperty0<C>,
    into: (String) -> T, from: (T) -> String,
    add: C.(T) -> C, remove: C.(T) -> C, empty: () -> C) {
    sys.addCommand(addCommand) {
      if (it.isNotEmpty()) field.set(field.get().add(into(it[0])))
    }

    sys.addCommand(clearCommand) {
      field.set(empty())
    }

    sys.addCommand(listCommand) {
      field.get().forEach {
        Logger.info("%s %s", addCommand, from(it))
      }
    }

    sys.addCommand(removeCommand) {
      if (it.isNotEmpty()) field.set(field.get().remove(into(it[0])))
    }

    sys.linkStore(addCommand, store)
    sys.setSerializer(addCommand) {
      listOf(clearCommand) + field.get().map { "$addCommand ${esc(from(it))}" }
    }
  }

  override fun <T> addList(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KMutableProperty0<List<T>>, into: (String) -> T, from: (T) -> String) {
    addROCollection(addCommand, clearCommand, listCommand, removeCommand, field, into, from, { this + it }, { this - it }, { emptyList() })
  }

  override fun <T> addSet(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KMutableProperty0<Set<T>>, into: (String) -> T, from: (T) -> String) {
    addROCollection(addCommand, clearCommand, listCommand, removeCommand, field, into, from, { this + it }, { this - it }, { emptySet() })
  }

  override fun <C : MutableCollection<T>, T> addCollection(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KProperty0<C>, into: (String) -> T, from: (T) -> String) {
    sys.addCommand(addCommand) {
      if (it.isNotEmpty()) field.get() += into(it[0])
    }

    sys.addCommand(clearCommand) {
      field.get().clear()
    }

    sys.addCommand(listCommand) {
      field.get().forEach {
        Logger.info("%s %s", addCommand, from(it))
      }
    }

    sys.addCommand(removeCommand) {
      if (it.isNotEmpty()) field.get() -= into(it[0])
    }

    sys.linkStore(addCommand, store)
    sys.setSerializer(addCommand) {
      listOf(clearCommand) + field.get().map { "$addCommand ${esc(from(it))}" }
    }
  }

  override fun <K, V> addMap(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KProperty0<MutableMap<K, V>>, intoKey: (String) -> K, intoValue: (String) -> V, fromKey: (K) -> String, fromValue: (V) -> String) {
    sys.addCommand(addCommand) {
      if (it.isNotEmpty()) field.get()[intoKey(it[0])] = intoValue(it[1])
    }

    sys.addCommand(clearCommand) {
      field.get().clear()
    }

    sys.addCommand(listCommand) {
      field.get()
        .toList()
        .sortedBy { (k, _) -> fromKey(k) }
        .forEach { (k, v) ->
          Logger.info("%s %s %s", addCommand, fromKey(k), fromValue(v))
        }
    }

    sys.addCommand(removeCommand) {
      if (it.isNotEmpty()) field.get().remove(intoKey(it[0]))
    }

    sys.linkStore(addCommand, store)
    sys.setSerializer(addCommand) {
      listOf(clearCommand) +
      field.get()
        .toList()
        .sortedBy { (k, _) -> fromKey(k) }
        .map { (k, v) -> "$addCommand ${esc(fromKey(k))} ${esc(fromValue(v))}" }
    }
  }

  override fun <K, V> addMap(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KMutableProperty0<Map<K, V>>, intoKey: (String) -> K, intoValue: (String) -> V, fromKey: (K) -> String, fromValue: (V) -> String) {
    sys.addCommand(addCommand) {
      if (it.isNotEmpty()) field.set(field.get() + Pair(intoKey(it[0]), intoValue(it[1])))
    }

    sys.addCommand(clearCommand) {
      field.set(emptyMap())
    }

    sys.addCommand(listCommand) {
      field.get()
        .toList()
        .sortedBy { (k, _) -> fromKey(k) }
        .forEach { (k, v) ->
          Logger.info("%s %s %s", addCommand, fromKey(k), fromValue(v))
        }
    }

    sys.addCommand(removeCommand) {
      if (it.isNotEmpty()) field.set(field.get() - intoKey(it[0]))
    }

    sys.linkStore(addCommand, store)
    sys.setSerializer(addCommand) {
      listOf(clearCommand) +
      field.get()
        .toList()
        .sortedBy { (k, _) -> fromKey(k) }
        .map { (k, v) -> "$addCommand ${esc(fromKey(k))} ${esc(fromValue(v))}" }
    }
  }
}

interface ValueStoreInit {

  fun <T> addCustom(prop: String, field: KMutableProperty0<T>, into: (String) -> T, from: (T) -> String)

  fun <C : MutableCollection<T>, T> addCollection(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KProperty0<C>, into: (String) -> T, from: (T) -> String)

  fun <C : MutableCollection<T>, T> addCollection(prop: String, field: KProperty0<C>, into: (String) -> T, from: (T) -> String) =
    addCollection(prop, "${prop}s_clear", "${prop}s", "${prop}_remove", field, into, from)

  fun <T> addList(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KMutableProperty0<List<T>>, into: (String) -> T, from: (T) -> String)

  fun <T> addList(prop: String, field: KMutableProperty0<List<T>>, into: (String) -> T, from: (T) -> String) =
    addList(prop, "${prop}s_clear", "${prop}s", "${prop}_remove", field, into, from)

  fun <T> addSet(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KMutableProperty0<Set<T>>, into: (String) -> T, from: (T) -> String)

  fun <T> addSet(prop: String, field: KMutableProperty0<Set<T>>, into: (String) -> T, from: (T) -> String) =
    addSet(prop, "${prop}s_clear", "${prop}s", "${prop}_remove", field, into, from)


  fun <K, V> addMap(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KProperty0<MutableMap<K, V>>, intoKey: (String) -> K, intoValue: (String) -> V, fromKey: (K) -> String, fromValue: (V) -> String)

  fun <K, V> addMap(prop: String, field: KProperty0<MutableMap<K, V>>, intoKey: (String) -> K, intoValue: (String) -> V, fromKey: (K) -> String, fromValue: (V) -> String) =
    addMap(prop, "${prop}s_clear", "${prop}s", "${prop}_remove", field, intoKey, intoValue, fromKey, fromValue)

  fun <K, V> addMap(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KMutableProperty0<Map<K, V>>, intoKey: (String) -> K, intoValue: (String) -> V, fromKey: (K) -> String, fromValue: (V) -> String)

  fun <K, V> addMap(prop: String, field: KMutableProperty0<Map<K, V>>, intoKey: (String) -> K, intoValue: (String) -> V, fromKey: (K) -> String, fromValue: (V) -> String) =
    addMap(prop, "${prop}s_clear", "${prop}s", "${prop}_remove", field, intoKey, intoValue, fromKey, fromValue)

  fun addString(prop: String, field: KMutableProperty0<String>) =
    addCustom(prop, field, { it }, { it })

  fun <C : MutableCollection<String>> addStrings(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KProperty0<C>) =
    addCollection(addCommand, clearCommand, listCommand, removeCommand, field, { it }, { it })

  fun <C : MutableCollection<String>> addStrings(prop: String, field: KProperty0<C>) =
    addCollection(prop, field, { it }, { it })

  fun addStrings(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KMutableProperty0<List<String>>) =
    addList(addCommand, clearCommand, listCommand, removeCommand, field, { it }, { it })

  fun addStrings(prop: String, field: KMutableProperty0<List<String>>) =
    addList(prop, field, { it }, { it })

  fun addStringSet(addCommand: String, clearCommand: String, listCommand: String, removeCommand: String, field: KMutableProperty0<Set<String>>) =
    addSet(addCommand, clearCommand, listCommand, removeCommand, field, { it }, { it })

  fun addStringSet(prop: String, field: KMutableProperty0<Set<String>>) =
    addSet(prop, field, { it }, { it })

  fun addInt(prop: String, field: KMutableProperty0<Int>, minValue: Int = 0, maxValue: Int = Int.MAX_VALUE) =
    addCustom(prop, field, { max(minValue, min(it.toIntOrNull() ?: 0, maxValue)) }, { it.toString() })

  fun addLong(prop: String, field: KMutableProperty0<Long>, minValue: Long = 0L, maxValue: Long = Long.MAX_VALUE) =
    addCustom(prop, field, { max(minValue, min(it.toLongOrNull() ?: 0L, maxValue)) }, { it.toString() })

  fun addBool(prop: String, field: KMutableProperty0<Boolean>) =
    addCustom(prop, field, { parseBoolean(it) }, { if (it) "1" else "0" })

}

interface ValueStore {

  fun init(ctx: ValueStoreInit)

  fun getDesc(prop: String): String? = null

  fun getFile(): String? = null

}

private fun tokenize2(s: String): List<String> {
  var escape = false
  var quote = false

  val result = mutableListOf<String>()
  var cur = ""

  loop@ for (c in s) {
    if (escape) {
      cur += c
      escape = false
    } else when (c) {
      in " \t\b\n\r\u000c" -> {
        if (quote) cur += c
        else {
          if (cur.isNotEmpty())
            result += cur
          cur = ""
        }
      }
      ';' -> {
        if (quote) cur += c
        else break@loop
      }
      '"' -> quote = !quote
      '\\' -> escape = true
      else -> cur += c
    }
  }

  if (cur.isNotEmpty())
    result += cur

  return result
}

private fun parseBoolean(s: String?) = s in setOf("true", "yes", "on") || s?.toIntOrNull() ?: 0 != 0