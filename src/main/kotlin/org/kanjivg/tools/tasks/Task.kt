package org.kanjivg.tools.tasks

import org.kanjivg.tools.KVGTag
import org.kanjivg.tools.parsing.KanjiSVGParser
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import javax.xml.stream.XMLInputFactory

/**
 * Base class for tasks.
 */
abstract class Task {
    protected final val logger = LoggerFactory.getLogger(javaClass)
    protected final val xmlInputFactory = createXMLInputFactory()

    /**
     * Create a domain object from a given file's contents
     */
    protected fun parse(file: File): KVGTag.SVG {
        logger.info("PARSING: {}", file.name)
        val eventReader = xmlInputFactory.createXMLEventReader(FileReader(file))
        try {
            return KanjiSVGParser.parse(eventReader)
        } finally {
            eventReader.close()
        }
    }

    private fun createXMLInputFactory(): XMLInputFactory {
        val xmlInputFactory = XMLInputFactory.newInstance()
        // 'kvg:' prefix causes error if namespace awareness is on
        xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
        xmlInputFactory.setProperty(XMLInputFactory.IS_VALIDATING, false)
        // Fetching DTD makes the parsing extremely slow
        xmlInputFactory.setXMLResolver { publicID, systemID, baseURI, namespace -> "".byteInputStream() }
        return xmlInputFactory
    }
}
