package org.kanjivg.tools

import com.typesafe.config.Config
import org.kanjivg.tools.parsing.KanjiSVGParser
import org.kanjivg.tools.parsing.ParsingException
import org.slf4j.LoggerFactory
import java.io.*
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
        // 'kvg:' prefix causes error if namespace awareness is on
        xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
        xmlInputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false)
        // Fetching DTD makes the parsing extremely slow
        xmlInputFactory.setXMLResolver { publicID, systemID, baseURI, namespace -> "".byteInputStream() }

        files.forEach { file ->
            logger.info("START: {}", file.name)
            val kanjiCode = file.nameWithoutExtension
            val eventReader = xmlInputFactory.createXMLEventReader(FileReader(file))
            try {
                val svg = KanjiSVGParser.parse(eventReader)
                logger.info("SUCCESSFUL: {}", file.name)
            } catch (e: ParsingException) {
                logger.error("PARSING FAILED: {}\n{}", file.name, e.message)
                logger.debug("Stack trace:", e)
            } catch (e: Throwable) {
                logger.error("UNHANDLED ERROR: {}", file.name, e)
            } finally {
                eventReader.close()
            }
        }
    }
}
