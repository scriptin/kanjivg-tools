package org.kanjivg.tools.tasks

import org.kanjivg.tools.parsing.KanjiSVGParser
import org.kanjivg.tools.validation.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.StartElement

object RepairIdsTask : Task() {
    fun repairIds(kvgDir: String, fileNameFilters: List<String>): Unit {
        val files = getFiles(kvgDir, fileNameFilters)
        val xmlInputFactory = createXMLInputFactory()
        files.forEach { file ->
            val eventReader = xmlInputFactory.createXMLEventReader(FileReader(file))
            try {
                val svg = KanjiSVGParser.parse(eventReader)
                val fileId = file.nameWithoutExtension
                val kanji = svg.strokePathsGroup.rootGroup.element?.value ?: "NA"
                val thingsToRepair = ThingsToRepair(
                    StrokeRootGroupId.validate(fileId, svg) is ValidationResult.Failed,
                    NumberRootGroupId.validate(fileId, svg) is ValidationResult.Failed,
                    StrokeGroupsIds.validate(fileId, svg) is ValidationResult.Failed,
                    StrokeIds.validate(fileId, svg) is ValidationResult.Failed
                )
                if (thingsToRepair.needsRepair()) {
                    logger.warn("Repairing IDs in {}/{}, overwriting a file", kanji, file.name)
                    val contents = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                    val repairedContents = repairXml(
                        xmlInputFactory.createXMLEventReader(FileReader(file)),
                        contents,
                        thingsToRepair,
                        fileId
                    )
                    file.writeBytes(repairedContents.toByteArray(StandardCharsets.UTF_8))
                } else {
                    logger.info("No changes required in {}/{}", kanji, file.name)
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
        class Root(override val children: MutableList<Tag>) : Tree("root", children) {
            override fun toString(): String = "Root(name='$name', children=$children)"
        }

        class Tag(
            val parent: Tree,
            override val name: String,
            override val children: MutableList<Tag>
        ) : Tree(name, children) {
            override fun toString(): String = "Tag(name='$name', children=$children)"
        }
    }

    private fun escape(s: String): String = s.replace("\n", "\\n").replace("\t", "\\t")

    private fun repairXml(
        sourceEventReader: XMLEventReader,
        contents: String,
        thingsToRepair: ThingsToRepair,
        fileId: String
    ): String {
        var updatedContents = contents
        var extraLength = 0
        try {
            val xml = Tree.Root(mutableListOf())
            var current: Tree = xml
            var groupIndex = 0
            var strokeIndex = 0
            var offsetStart = -1
            var offsetEnd = 0
            while (sourceEventReader.hasNext()) {
                val event = sourceEventReader.nextEvent()
                offsetStart = if (offsetStart == -1) 0 else offsetEnd
                offsetEnd = if (event.location.characterOffset > 0) {
                    event.location.characterOffset
                } else {
                    contents.length
                }
                if (event.isCharacters) {
                    // Characters event before opening/closing have character offset with "<"/"</" included
                    val substring = contents.substring(offsetStart, offsetEnd)
                    if (substring.endsWith("</")) {
                        offsetEnd -= 2
                    } else if (substring.endsWith("<")) {
                        offsetEnd -= 1
                    }
                }
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "eventType: {}, offsetStart: {}, offsetEnd: {}\n===\n{}\n{}\n===",
                        event.eventType, offsetStart, offsetEnd,
                        escape(contents.substring(offsetStart, offsetEnd)),
                        escape(event.toString())
                    )
                }
                if (event.isStartElement) {
                    val startElement = event.asStartElement()
                    val tag = Tree.Tag(current, startElement.name.localPart, mutableListOf())
                    current.children.add(tag)
                    current = tag
                    val (newContents, lengthDelta) = repairIfNecessary(
                        updatedContents, offsetStart, offsetEnd, extraLength,
                        startElement,
                        tag,
                        thingsToRepair,
                        fileId,
                        groupIndex, strokeIndex
                    )
                    updatedContents = newContents
                    extraLength += lengthDelta
                    if (tag.name == "g" && tag.parent.name == "g") {
                        groupIndex += 1
                    }
                    if (tag.name == "path") {
                        strokeIndex += 1
                    }
                } else if (event.isEndElement) {
                    current = (current as Tree.Tag).parent
                }
            }
            logger.debug("XML tree: {}", xml)
        } finally {
            sourceEventReader.close()
        }
        return updatedContents
    }

    private fun repairIfNecessary(
        contents: String,
        offsetStart: Int,
        offsetEnd: Int,
        extraLength: Int,
        startElement: StartElement,
        tag: Tree.Tag,
        thingsToRepair: ThingsToRepair,
        fileId: String,
        groupIndex: Int,
        strokeIndex: Int
    ): Pair<String, Int> {
        if ( ! thingsToRepair.needsRepair()) return Pair(contents, 0)
        if (tag.name == "g" && tag.parent.name == "svg") {
            if (thingsToRepair.strokeRootGroupId && tag.parent.children.indexOf(tag) == 0) {
                return replaceIdIfNecessary(
                    contents, offsetStart, offsetEnd, extraLength,
                    startElement, StrokeRootGroupId.getExpectedId(fileId)
                )
            }
            if (thingsToRepair.numberRootGroupId && tag.parent.children.indexOf(tag) == 1) {
                return replaceIdIfNecessary(
                    contents, offsetStart, offsetEnd, extraLength,
                    startElement, NumberRootGroupId.getExpectedId(fileId)
                )
            }
        }
        if (thingsToRepair.strokeGroupIds && tag.name == "g" && tag.parent.name == "g") {
            return replaceIdIfNecessary(
                contents, offsetStart, offsetEnd, extraLength,
                startElement, StrokeGroupsIds.getExpectedId(fileId, groupIndex)
            )
        }
        if (thingsToRepair.strokeIds && tag.name == "path") {
            return replaceIdIfNecessary(
                contents, offsetStart, offsetEnd, extraLength,
                startElement, StrokeIds.getExpectedId(fileId, strokeIndex)
            )
        }
        return Pair(contents, 0)
    }

    private fun replaceIdIfNecessary(
        contents: String,
        offsetStart: Int,
        offsetEnd: Int,
        extraLength: Int,
        startElement: StartElement,
        expectedId: String
    ): Pair<String, Int> {
        val actualId = startElement.getAttributeByName(QName("id")).value
        val start = offsetStart + extraLength
        val end = offsetEnd + extraLength
        if (actualId != expectedId) {
            logger.debug("actual ID is '{}', expected '{}'", actualId, expectedId)
            val before = if (start == 0) "" else contents.substring(0, start)
            val element = contents.substring(start, end).replace("id=\"$actualId\"", "id=\"$expectedId\"")
            val after = if (end == contents.length) "" else contents.substring(end, contents.length)
            return Pair(before + element + after, expectedId.length - actualId.length)
        }
        return Pair(contents, 0)
    }
}

