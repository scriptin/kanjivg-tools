package org.kanjivg.tools.tasks

import org.kanjivg.tools.KVGTag
import org.kanjivg.tools.tasks.config.FilesConfig
import java.io.FileReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.StartElement

/**
 * Base class for tasks which fix attributes of elements
 */
abstract class RepairXMLAttributeTask<T : ThingsToRepair> : Task() {
    fun repair(filesConfig: FilesConfig): Unit {
        filesConfig.getFiles().forEach { file ->
            val svg = parse(file)
            val fileId = file.nameWithoutExtension
            val kanji = svg.strokePathsGroup.rootGroup.element?.value ?: "NA"
            val thingsToRepair = findThingsToRepair(fileId, svg)
            if (thingsToRepair.needsRepair()) {
                logger.warn("Repairing IDs in {}/{}, overwriting a file", kanji, file.name)
                val contents = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                val repairedContents = repairXml(
                    svg,
                    xmlInputFactory.createXMLEventReader(FileReader(file)),
                    contents,
                    thingsToRepair,
                    fileId
                )
                file.writeBytes(repairedContents.toByteArray(StandardCharsets.UTF_8))
            } else {
                logger.info("No changes required in {}/{}", kanji, file.name)
            }
        }
    }

    abstract protected fun findThingsToRepair(fileId: String, svg: KVGTag.SVG): T

    /**
     * XML tree, used to track location inside a file
     */
    protected sealed class Tree(open val name: String, open val children: MutableList<Tag>) {
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
        svg: KVGTag.SVG,
        sourceEventReader: XMLEventReader,
        contents: String,
        thingsToRepair: T,
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
                        svg,
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

    abstract protected fun repairIfNecessary(
        svg: KVGTag.SVG,
        contents: String,
        offsetStart: Int,
        offsetEnd: Int,
        extraLength: Int,
        startElement: StartElement,
        tag: Tree.Tag,
        thingsToRepair: T,
        fileId: String,
        groupIndex: Int,
        strokeIndex: Int
    ): Pair<String, Int>
}
