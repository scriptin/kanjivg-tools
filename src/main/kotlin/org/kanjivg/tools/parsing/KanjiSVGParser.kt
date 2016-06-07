package org.kanjivg.tools.parsing

import java.util.*
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.Characters
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent

object KanjiSVGParser {
    private const val SVG_NS = "http://www.w3.org/2000/svg"
    private const val KVG_NS = "http://kanjivg.tagaini.net"
    private const val KVG_PREFIX = "kvg"

    private val TAG_SVG = QName(SVG_NS, "svg")
    private val TAG_GROUP = QName(SVG_NS, "g")
    private val TAG_TEXT = QName(SVG_NS, "text")

    private val ATTR_ID = QName("id")
    private val ATTR_WIDTH = QName(SVG_NS, "width")
    private val ATTR_HEIGHT = QName(SVG_NS, "height")
    private val ATTR_VIEW_BOX = QName(SVG_NS, "viewBox")
    private val ATTR_STYLE = QName(SVG_NS, "style")
    private val ATTR_TRANSFORM = QName(SVG_NS, "transform")

    fun parse(eventReader: XMLEventReader): KVGTag.SVG {
        skip(eventReader, XMLEvent.START_DOCUMENT, XMLEvent.SPACE) // <?xml ...
        skip(eventReader, XMLEvent.COMMENT, XMLEvent.SPACE) // <!-- Copyright ...
        skip(eventReader, XMLEvent.DTD, XMLEvent.SPACE) // <!DOCTYPE ...
        return svg(eventReader)
    }

    // UTILITY METHODS:

    private fun skip(eventReader: XMLEventReader, vararg eventsToSkip: Int): Unit {
        while (eventReader.hasNext()) {
            if (eventsToSkip.contains(eventReader.peek().eventType)) {
                eventReader.nextEvent()
            } else {
                break
            }
        }
    }

    private fun skipSpace(eventReader: XMLEventReader): Unit {
        skip(eventReader, XMLEvent.SPACE)
    }

    private fun nextEvent(eventReader: XMLEventReader): XMLEvent {
        try {
            return eventReader.nextEvent()
        } catch (e: NoSuchElementException) {
            throw ParsingException.UnexpectedEndOfDocumentException(e)
        }
    }

    private fun openTag(name: QName, description: String, eventReader: XMLEventReader): StartElement {
        val event = nextEvent(eventReader)
        if ( ! event.isStartElement) {
            throw ParsingException.MissingOpenTagException(name, description, event)
        } else if (event.asStartElement().name != name) {
            throw ParsingException.UnexpectedOpenTagException(name, description, event.asStartElement())
        } else {
            return event.asStartElement()
        }
    }

    private fun closeTag(name: QName, description: String, eventReader: XMLEventReader): Unit {
        val event = nextEvent(eventReader)
        if ( ! event.isEndElement) {
            throw ParsingException.MissingCloseTagException(name, description, event)
        } else if (event.asEndElement().name != TAG_SVG) {
            throw ParsingException.UnexpectedCloseTagException(name, description, event.asEndElement())
        }
    }

    private fun <E> childrenList(extractor: (XMLEventReader) -> E?, eventReader: XMLEventReader): List<E> {
        val result = mutableListOf<E>()
        while (eventReader.hasNext()) {
            skipSpace(eventReader)
            result.add(extractor(eventReader) ?: break)
        }
        return result.toList()
    }

    private fun <E> nonEmptyChildrenList(
        parentTag: StartElement,
        childTagName: QName,
        extractor: (XMLEventReader) -> E?,
        eventReader: XMLEventReader
    ): List<E> {
        val result = childrenList(extractor, eventReader)
        if (result.isEmpty()) {
            throw ParsingException.EmptyChildrenListException(parentTag, childTagName)
        }
        return result
    }

    private fun characters(parentTag: StartElement, eventReader: XMLEventReader): Characters {
        val event = nextEvent(eventReader)
        if ( ! event.isCharacters || event.asCharacters().isCData || event.asCharacters().isIgnorableWhiteSpace) {
            throw ParsingException.MissingCharactersException(parentTag, event)
        }
        return event.asCharacters()
    }

    private fun requiredAttr(element: StartElement, name: QName): Attribute {
        return element.getAttributeByName(name) ?:
            throw ParsingException.MissingRequiredAttributeException(element, name)
    }

    private fun requiredStringAttr(element: StartElement, name: QName): String {
        return requiredAttr(element, name).value
    }

    private fun requiredIntAttr(element: StartElement, name: QName): Int {
        val attr = requiredAttr(element, name)
        try {
            return attr.value.toInt()
        } catch (e: NumberFormatException) {
            throw ParsingException.AttributeFormatException(element, attr, e.message ?: "invalid number format", e)
        }
    }

    // TAGS PARSING:

    private fun svg(eventReader: XMLEventReader): KVGTag.SVG {
        val element = openTag(TAG_SVG, "opening root tag", eventReader)
        val width = width(element)
        val height = height(element)
        val viewBox = viewBox(element)

        skipSpace(eventReader)
        val strokePathsGroup = strokePathsGroup(eventReader)
        skipSpace(eventReader)
        val strokeNumbersGroup = strokeNumbersGroup(eventReader)
        skipSpace(eventReader)

        closeTag(TAG_SVG, "closing root tag", eventReader)
        return KVGTag.SVG(width, height, viewBox, strokePathsGroup, strokeNumbersGroup)
    }

    private fun strokePathsGroup(eventReader: XMLEventReader): KVGTag.StrokePathsGroup {
        val element = openTag(TAG_GROUP, "stroke paths group open tag", eventReader)
        val id = id(element)
        val style = style(element)
        // TODO: get children
        closeTag(TAG_GROUP, "stroke paths group closing tag", eventReader)
        return KVGTag.StrokePathsGroup(id, style, children)
    }

    private fun strokeNumbersGroup(eventReader: XMLEventReader): KVGTag.StrokeNumbersGroup {
        val element = openTag(TAG_GROUP, "stroke numbers group open tag", eventReader)
        val id = id(element)
        val style = style(element)

        skipSpace(eventReader)
        val children = nonEmptyChildrenList(element, TAG_TEXT, { strokeNumber(it) }, eventReader)
        skipSpace(eventReader)

        closeTag(TAG_GROUP, "stroke numbers group closing tag", eventReader)
        return KVGTag.StrokeNumbersGroup(id, style, children)
    }

    private fun strokeNumber(eventReader: XMLEventReader): KVGTag.StrokeNumber? {
        val element = openTag(TAG_TEXT, "stroke number open tag", eventReader)
        val transform = transform(element)
        val number = strokeNumberValue(element, eventReader)
        closeTag(TAG_TEXT, "stroke number closing tag", eventReader)
        return KVGTag.StrokeNumber(number, transform)
    }

    private fun strokeNumberValue(element: StartElement, eventReader: XMLEventReader): Int {
        val characters = characters(element, eventReader)
        try {
            return characters.data.trim().toInt()
        } catch (e: NumberFormatException) {
            throw ParsingException.CharactersFormatException(
                element, characters.data, "expected an integer", e
            )
        }
    }

    // ATTRIBUTES PARSING:

    private fun width(element: StartElement): KVGTag.Attribute.Width =
        KVGTag.Attribute.Width(requiredIntAttr(element, ATTR_WIDTH))

    private fun height(element: StartElement): KVGTag.Attribute.Height =
        KVGTag.Attribute.Height(requiredIntAttr(element, ATTR_HEIGHT))

    private fun viewBox(element: StartElement): KVGTag.Attribute.ViewBox {
        val attr = requiredAttr(element, ATTR_VIEW_BOX)
        try {
            val parts = attr.value.trim().split(Regex("[,\\s]+"))
            if (parts.size != 4) {
                throw ParsingException.AttributeFormatException(
                    element, attr,
                    "there must be 4 integer parts in this element, separated by commas and/or whitespace"
                )
            }
            val x = parts[0].toInt()
            val y = parts[1].toInt()
            val w = parts[2].toInt()
            val h = parts[3].toInt()
            if (w < 0) {
                throw ParsingException.AttributeFormatException(
                    element, attr,
                    "width (3rd element) cannot be less than 0"
                )
            }
            if (h < 0) {
                throw ParsingException.AttributeFormatException(
                    element, attr,
                    "height (4th element) cannot be less than 0"
                )
            }
            return KVGTag.Attribute.ViewBox(x, y, w, h)
        } catch (e: NumberFormatException) {
            throw ParsingException.AttributeFormatException(
                element, attr,
                e.message ?: "invalid number format", e
            )
        }
    }

    private fun id(element: StartElement): KVGTag.Attribute.Id {
        return KVGTag.Attribute.Id(requiredStringAttr(element, ATTR_ID))
    }

    private fun style(element: StartElement): KVGTag.Attribute.Style {
        val attr = requiredAttr(element, ATTR_STYLE)
        val parts = attr.value.trim().split(Regex("[;\\s]+")).map { part ->
            val pair = part.split(Regex("[:\\s]+"))
            if (pair.size != 2) {
                throw ParsingException.AttributeFormatException(
                    element, attr,
                    "the '$part' part must have a format of 'key: value'"
                )
            }
            Pair(pair[0], pair[1])
        }.toMap()
        return KVGTag.Attribute.Style(parts)
    }

    private fun transform(element: StartElement): KVGTag.Attribute.Transform {
        val attr = requiredAttr(element, ATTR_TRANSFORM)
        val rawString = attr.value.trim()
        if ( ! rawString.matches(Regex("^matrix(.*)$"))) {
            throw ParsingException.AttributeFormatException(element, attr, "expected 'matrix(...)'")
        }
        val numbers = rawString.split("(")[1].split(")")[0].trim().split(Regex("[,\\s]+"))
        if (numbers.size != 6) {
            throw ParsingException.AttributeFormatException(
                element, attr,
                "expected exactly 6 elements inside 'matrix(...)'"
            )
        }
        try {
            return KVGTag.Attribute.Transform(
                KVGTag.Attribute.Transform.Matrix(numbers.map { it.toDouble() })
            )
        } catch (e: NumberFormatException) {
            throw ParsingException.AttributeFormatException(
                element, attr,
                "expected only numeric values inside 'matrix(...)'"
            )
        }
    }
}
