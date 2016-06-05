package org.kanjivg.tools

import com.typesafe.config.Config
import org.kanjivg.tools.parsing.KanjiSVGParser
import org.kanjivg.tools.parsing.ParsingException
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.xml.stream.XMLInputFactory

class Tools(final val config: Config) {
    private final val logger = LoggerFactory.getLogger(javaClass)

    fun validate(): Unit {
        val dir = config.getString("kanjivgDir")
        logger.info("Scanning KanjiVG directory: {}", dir)
        val files: List<File> = File(dir).walk().onFail { file, ioException ->
            throw RuntimeException("Error processing a file '$file'", ioException)
        }.filter { it.isFile }.toList()
        logger.info("Found {} files", files.size)

        val xmlInputFactory = XMLInputFactory.newInstance()

        Stream.of(*files.toTypedArray()).parallel().map { file ->
            try {
                logger.info("START: {}", file.name)
                val kanjiCode = file.nameWithoutExtension
                logger.info("PARSING START: {}", file.name)
                val svg = KanjiSVGParser.parse(xmlInputFactory.createXMLEventReader(FileReader(file)))
                logger.info("PARSING END: {}", file.name)
            } catch (e: ParsingException) {
                logger.error("PARSING FAILED: {}: ${e.message}")
                logger.debug("Stack trace:", e)
            } catch (e: Throwable) {
                logger.error("UNHANDLED ERROR:", e)
            } finally {
                logger.info("END: {}", file.name)
            }
        }.collect(Collectors.toList())
    }
}
