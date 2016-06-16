package org.kanjivg.tools.tasks

import org.kanjivg.tools.parsing.KanjiSVGParser
import org.kanjivg.tools.validation.*
import java.io.*
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventFactory
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLEventWriter
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.StartElement

object RepairIdsTask : Task() {
    fun repairIds(kvgDir: String, fileNameFilters: List<String>): Unit {
        val files = getFiles(kvgDir, fileNameFilters)
        val xmlInputFactory = createXMLInputFactory()
        val xmlOutputFactory = createXMLOutputFactory()
        val xmlEventFactory = XMLEventFactory.newFactory()
        files.forEach { file ->
            val fileId = file.nameWithoutExtension
            val eventReader = xmlInputFactory.createXMLEventReader(FileReader(file))
            try {
                val svg = KanjiSVGParser.parse(eventReader)
                val thingsToRepair = ThingsToRepair(
                    StrokeRootGroupId.validate(fileId, svg) is ValidationResult.Failed,
                    NumberRootGroupId.validate(fileId, svg) is ValidationResult.Failed,
                    StrokeGroupsIds.validate(fileId, svg) is ValidationResult.Failed,
                    StrokeIds.validate(fileId, svg) is ValidationResult.Failed
                )
                if (thingsToRepair.needsRepair()) {
                    val updatedFileName = "$kvgDir${File.separator}$fileId-fixed-ids.svg"
                    logger.warn("Repairing IDs in {}, writing into {}", fileId, updatedFileName)
                    repairXml(
                        xmlInputFactory.createXMLEventReader(FileReader(file)),
                        xmlOutputFactory.createXMLEventWriter(FileWriter(updatedFileName)),
                        xmlEventFactory,
                        thingsToRepair,
                        fileId
                    )
                } else {
                    logger.info("No changes required in {}", fileId)
                }
            } finally {
                eventReader.close()
            }
        }
    }

    /**
     * DTO for repair flags
     */
    private class ThingsToRepair(
        val strokeRootGroupId: Boolean,
        val numberRootGroupId: Boolean,
        val strokeGroupIds: Boolean,
        val strokeIds: Boolean
    ) {
        fun needsRepair(): Boolean = strokeRootGroupId || numberRootGroupId || strokeGroupIds || strokeIds
    }

    /**
     * XML tree, used to track location inside a file
     */
    private sealed class Tree(open val name: String, open val children: MutableList<Tag>) {
        class Root(override val children: MutableList<Tag>) : Tree("root", children)
        class Tag(
            val parent: Tree,
            override val name: String,
            override val children: MutableList<Tag>
        ) : Tree(name, children)
    }

    private fun repairXml(
        sourceEventReader: XMLEventReader,
        targetEventWriter: XMLEventWriter,
        eventFactory: XMLEventFactory,
        thingsToRepair: ThingsToRepair,
        fileId: String
    ): Unit {
        try {
            val xml = Tree.Root(mutableListOf())
            var current: Tree = xml
            var groupIndex = 0
            var strokeIndex = 0
            while (sourceEventReader.hasNext()) {
                val event = sourceEventReader.peek()
                if (event.isStartElement) {
                    val tag = Tree.Tag(current, event.asStartElement().name.localPart, mutableListOf())
                    current.children.add(tag)
                    current = tag
                    targetEventWriter.add(repairIfNecessary(
                        eventFactory,
                        event.asStartElement(),
                        tag,
                        thingsToRepair,
                        fileId,
                        groupIndex, strokeIndex
                    ))
                    if (tag.name == "g" && tag.parent.name == "g") {
                        groupIndex += 1
                    }
                    if (tag.name == "path") {
                        strokeIndex += 1
                    }
                    sourceEventReader.nextEvent() // flush original
                } else if (event.isEndElement) {
                    current = (current as Tree.Tag).parent
                    targetEventWriter.add(sourceEventReader.nextEvent())
                } else {
                    targetEventWriter.add(sourceEventReader.nextEvent())
                }
            }
            targetEventWriter.flush()
        } finally {
            sourceEventReader.close()
            targetEventWriter.close()
        }
    }

    private fun repairIfNecessary(
        eventFactory: XMLEventFactory,
        startElement: StartElement,
        tag: Tree.Tag,
        thingsToRepair: ThingsToRepair,
        fileId: String,
        groupIndex: Int,
        strokeIndex: Int
    ): StartElement {
        if ( ! thingsToRepair.needsRepair()) {
            return startElement
        }
        if (tag.name == "g" && tag.parent.name == "svg") {
            if (thingsToRepair.strokeRootGroupId && tag.parent.children.indexOf(tag) == 0) {
                return replaceIdIfNecessary(eventFactory, startElement, StrokeRootGroupId.getExpectedId(fileId))
            }
            if (thingsToRepair.numberRootGroupId && tag.parent.children.indexOf(tag) == 1) {
                return replaceIdIfNecessary(eventFactory, startElement, NumberRootGroupId.getExpectedId(fileId))
            }
        }
        if (thingsToRepair.strokeGroupIds && tag.name == "g" && tag.parent.name == "g") {
            return replaceIdIfNecessary(eventFactory, startElement, StrokeGroupsIds.getExpectedId(fileId, groupIndex))
        }
        if (thingsToRepair.strokeIds && tag.name == "path") {
            return replaceIdIfNecessary(eventFactory, startElement, StrokeIds.getExpectedId(fileId, strokeIndex))
        }
        return startElement
    }

    private fun replaceIdIfNecessary(
        eventFactory: XMLEventFactory,
        startElement: StartElement,
        expectedId: String
    ): StartElement {
        val actualId = startElement.getAttributeByName(QName("id")).value
        logger.debug("actual ID={}, expected ID={}", actualId, expectedId)
        if (actualId != expectedId) {
            @Suppress("UNCHECKED_CAST")
            val attributes = (startElement.attributes as Iterator<Attribute>).asSequence().map { attr ->
                if (attr.name.localPart == "id") {
                    eventFactory.createAttribute(attr.name, expectedId)
                } else {
                    attr
                }
            }.iterator()
            return eventFactory.createStartElement(
                startElement.name,
                attributes,
                startElement.namespaces
            )
        }
        return startElement
    }
}

