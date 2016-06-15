package org.kanjivg.tools.tasks

import org.kanjivg.tools.KVGTag
import org.kanjivg.tools.parsing.KanjiSVGParser
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory

/**
 * Base class for tasks.
 */
abstract class Task {
    protected final val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Parse a file into object
     */
    protected fun parse(file: File, xmlInputFactory: XMLInputFactory): KVGTag.SVG {
        logger.info("PARSING: {}", file.name)
        val eventReader = xmlInputFactory.createXMLEventReader(FileReader(file))
        try {
            return KanjiSVGParser.parse(eventReader)
        } finally {
            eventReader.close()
        }
    }

    /**
     * Get filtered list of files from given directory
     */
    protected fun getFiles(dir: String, filters: List<String>): List<File> {
        logger.info("Scanning KanjiVG directory: {}", dir)
        logger.info("Using filters: $filters")
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
        logger.info("Found {} files matching filters", files.size)
        return files
    }

    /**
     * Escape filter strings: '*' becomes '.*', everything else is treated as literal
     */
    private fun prepareFilters(filters: List<String>): List<Regex> {
        return filters.map { filter ->
            Regex(filter.split("*").map { Regex.escape(it) }.joinToString(".*"))
        }
    }

    /**
     * Create new instance of [XMLInputFactory]
     */
    protected fun createXMLInputFactory(): XMLInputFactory {
        val xmlInputFactory = XMLInputFactory.newInstance()
        // 'kvg:' prefix causes error if namespace awareness is on
        xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
        xmlInputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false)
        // Fetching DTD makes the parsing extremely slow
        xmlInputFactory.setXMLResolver { publicID, systemID, baseURI, namespace -> "".byteInputStream() }
        return xmlInputFactory
    }

    /**
     * Create new instance of [XMLOutputFactory]
     */
    protected fun createXMLOutputFactory(): XMLOutputFactory = XMLOutputFactory.newFactory()
}
