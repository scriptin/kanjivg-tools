package org.kanjivg.tools

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

private enum class TaskType {
    VALIDATE
}

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("org.kanjivg.tools.main")
    val config = ConfigFactory.load()
    val tools = Tools()
    val taskId = config.getString("task")
    val task = try {
        TaskType.valueOf(taskId.toUpperCase())
    } catch (e: IllegalArgumentException) {
        throw RuntimeException(
            "Task with id $taskId (or ${taskId.toUpperCase()}) does not exist, " +
                "expected one of: ${TaskType.values().toList()}",
            e
        )
    }
    logger.info(frame("task: ${task.name}"))
    val kanjiVGDir = config.getString("kanjivg.dir")
    when (task) {
        TaskType.VALIDATE -> {
            val fileNameFilters = config.getString("${TaskType.VALIDATE.name.toLowerCase()}.files").split(",")
            tools.validate(kanjiVGDir, fileNameFilters)
        }
    }
}

private fun frame(message: String): String {
    val l = message.length + 6
    val border = "=".repeat(l)
    return "\n$border\n== $message ==\n$border\n"
}
