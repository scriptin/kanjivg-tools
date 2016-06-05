package org.kanjivg.tools

import com.typesafe.config.ConfigFactory

fun main(args: Array<String>) {
    val tools = Tools(ConfigFactory.load())
    tools.validate()
}
