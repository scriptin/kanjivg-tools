package org.kanjivg.tools.tasks

import org.kanjivg.tools.KVGTag
import org.kanjivg.tools.validation.*
import javax.xml.namespace.QName
import javax.xml.stream.events.StartElement

object RepairIdsTask : RepairXMLAttributeTask<IdsToRepair>() {
    override fun findThingsToRepair(fileId: String, svg: KVGTag.SVG) = IdsToRepair(
        StrokeRootGroupId.validate(fileId, svg) is ValidationResult.Failed,
        NumberRootGroupId.validate(fileId, svg) is ValidationResult.Failed,
        StrokeGroupsIds.validate(fileId, svg) is ValidationResult.Failed,
        StrokeIds.validate(fileId, svg) is ValidationResult.Failed
    )

    override fun repairIfNecessary(
        svg: KVGTag.SVG,
        contents: String,
        offsetStart: Int,
        offsetEnd: Int,
        extraLength: Int,
        startElement: StartElement,
        tag: Tree.Tag,
        thingsToRepair: IdsToRepair,
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

