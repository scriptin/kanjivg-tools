package org.kanjivg.tools

import com.typesafe.config.Config
import org.kanjivg.tools.parsing.KanjiSVGParser
import org.kanjivg.tools.parsing.ParsingException
import org.kanjivg.tools.validation.*
import org.slf4j.LoggerFactory
import java.io.*
import javax.xml.stream.XMLInputFactory

class Tools(final val config: Config) {
    private final val logger = LoggerFactory.getLogger(javaClass)

    private final val validations = listOf(
        WidthAndHeight,
        ViewBox,
        StrokeRootGroupId,
        StrokeRootGroupStyle,
        StrokeGroupsIds,
        StrokeIds,
        NumberRootGroupId,
        NumberRootGroupStyle,
        StrokeNumbersCount
    )

    fun validate(): Unit {
        val files = getFiles(config.getString("kanjivgDir"))
        val xmlInputFactory = getXMLInputFactory()
        printValidationsInfo(validations)
        files.forEach { file ->
            logger.info("===== {} =====", file.name)
            val kanjiCode = file.nameWithoutExtension
            val eventReader = xmlInputFactory.createXMLEventReader(FileReader(file))
            try {
                val svg = KanjiSVGParser.parse(eventReader)
                val failedValidations = validations
                    .map { Pair(it.name, it.validate(kanjiCode, svg)) }
                    .filter { pair -> pair.second is ValidationResult.Failed }
                if (failedValidations.isEmpty()) {
                    logger.info("ALL VALIDATIONS PASSED: {}", file.name)
                } else {
                    logger.warn(
                        "SOME VALIDATIONS FAILED: {}\n{}\n", file.name,
                        failedValidations.map { "  - ${it.first}: ${it.second}" }.joinToString("\n")
                    )
                }
            } catch (e: ParsingException) {
                logger.error("PARSING FAILED: {} - {}", file.name, e.message)
                logger.debug("Stack trace:", e)
            } catch (e: Throwable) {
                logger.error("UNHANDLED ERROR: {}", file.name, e)
            } finally {
                eventReader.close()
            }
        }
    }

    private fun getFiles(dir: String): List<File> {
        logger.info("Scanning KanjiVG directory: {}", dir)
        val files: List<File> = File(dir).walk().onFail { file, ioException ->
            throw RuntimeException("Error processing a file '$file'", ioException)
        }.filter { it.isFile }.toList()
        logger.info("Found {} files", files.size)
        return files
    }

    private fun getXMLInputFactory(): XMLInputFactory {
        val xmlInputFactory = XMLInputFactory.newInstance()
        // 'kvg:' prefix causes error if namespace awareness is on
        xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
        xmlInputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false)
        // Fetching DTD makes the parsing extremely slow
        xmlInputFactory.setXMLResolver { publicID, systemID, baseURI, namespace -> "".byteInputStream() }
        return xmlInputFactory
    }

    private fun printValidationsInfo(validations: List<Validation>) {
        val validationDescriptions = validations.map { "${it.name}: ${it.description}" }
        logger.info("Validations:\n  ${validationDescriptions.joinToString("\n  ")}")
    }
}
