import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender

import static ch.qos.logback.classic.Level.*

def getLogLevel() {
    def level = System.getProperty('log.level')?.toUpperCase() ?: ""
    if ( ! level.isEmpty()) {
        valueOf(level)
    } else {
        WARN
    }
}

appender("STDOUT", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%d{HH:mm:ss.SSS} %level - %msg%n%ex"
    }
}

logger("org.kanjivg.tools", getLogLevel())

root(WARN, ["STDOUT"])
