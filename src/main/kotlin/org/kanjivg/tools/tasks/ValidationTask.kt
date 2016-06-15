package org.kanjivg.tools.tasks

import org.kanjivg.tools.parsing.KanjiSVGParser
import org.kanjivg.tools.parsing.ParsingException
import org.kanjivg.tools.validation.*
import org.slf4j.LoggerFactory
import java.io.*
import javax.xml.stream.XMLInputFactory

object ValidationTask {
    private final val logger = LoggerFactory.getLogger(javaClass)

    /**
     * List of available validations.
     * TODO: make it configurable with application.conf and command-line arguments
     */
    private final val validations = listOf(
        WidthAndHeight,
        ViewBox,
        StrokeRootGroupId,
        StrokeRootGroupStyle,
        StrokeGroupsIds,
        StrokeIds,
        NumberRootGroupId,
        NumberRootGroupStyle,
        StrokeNumbersCount,
        NumberOrder
    )

    /**
     * Entry point for parsing and validation
     */
    fun validate(kvgDir: String, fileNameFilters: List<String>): Unit {
        val files = getFiles(kvgDir, fileNameFilters)
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
            } finally {
                eventReader.close()
            }
        }
    }

    /**
     * Escape filter strings: '*' becomes '.*', everything else is treated as literal
     */
    private fun prepareFilters(filters: List<String>): List<Regex> {
        return filters.map { filter ->
            Regex(filter.split("*").map { Regex.escape(it) }.joinToString(".*"))
        }
    }

    private fun getFiles(dir: String, filters: List<String>): List<File> {
        logger.info("Scanning KanjiVG directory: {}", dir)
        val regexFilters = prepareFilters(filters)
        val files: List<File> = File(dir).walk()
            .onFail { file, ioException ->
                throw RuntimeException("Error processing a file '$file'", ioException)
            }
            .filter { it.isFile }
            .filter { file ->
                regexFilters.indexOfFirst { flt -> flt.matches(file.nameWithoutExtension) } >= 0
            }
            .toList()
        logger.info("Found {} files matching filters: {}", files.size, filters)
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
        logger.info("Enabled validations:\n  ${validationDescriptions.joinToString("\n  ")}")
    }
}
