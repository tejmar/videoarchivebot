package therealfarfetchd.videoarchivebot

import therealfarfetchd.videoarchivebot.LogType.TASK
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

private val tasks: MutableSet<TaskRef> = Collections.synchronizedSet(mutableSetOf())

fun task(name: String, op: TaskContext.() -> Unit): Task {
  val taskRef = TaskRef(name)
  val thread = thread(start = false, name = name) {
    try {
      Logger.log(TASK, "+ %s", name)
      op(TaskContext(taskRef.lock))
    } finally {
      Logger.log(TASK, "- %s", name)
      tasks -= taskRef
    }
  }
  taskRef.thread = thread
  tasks += taskRef
  thread.start()
  return taskRef
}

class TaskContext(val lock: Lock)

interface Task {
  val name: String

  fun stop()

  fun isRunning(): Boolean
}

private class TaskRef(override val name: String) : Task {
  lateinit var thread: Thread

  val lock = ReentrantLock(true)

  override fun stop() {
    if (isRunning()) lock {
      thread.stop()
    }
    tasks -= this
  }

  override fun isRunning() = thread.isAlive
}

fun stopTasks() {
  tasks.toList().forEach { it.stop() }
}

fun getTasks(): Set<Task> = tasks