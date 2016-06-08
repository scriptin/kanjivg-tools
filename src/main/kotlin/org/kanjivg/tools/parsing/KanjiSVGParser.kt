package org.kanjivg.tools.parsing

import java.util.*
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.*

import org.kanjivg.tools.parsing.KVGTag.Attribute as Attr

object KanjiSVGParser {
    private const val SVG_NS = "http://www.w3.org/2000/svg"
    private const val KVG_NS = "http://kanjivg.tagaini.net"
    private const val KVG_PREFIX = "kvg"

    private val TAG_SVG = QName(SVG_NS, "svg")
    private val TAG_GROUP = QName(SVG_NS, "g")
    private val TAG_PATH = QName(SVG_NS, "path")
    private val TAG_TEXT = QName(SVG_NS, "text")

    private val ATTR_ID = QName("id")
    private val ATTR_WIDTH = QName(SVG_NS, "width")
    private val ATTR_HEIGHT = QName(SVG_NS, "height")
    private val ATTR_VIEW_BOX = QName(SVG_NS, "viewBox")
    private val ATTR_STYLE = QName(SVG_NS, "style")
    private val ATTR_TRANSFORM = QName(SVG_NS, "transform")

    private val ATTR_ELEMENT = QName(KVG_NS, "element", KVG_PREFIX)
    private val ATTR_ORIGINAL = QName(KVG_NS, "original", KVG_PREFIX)
    private val ATTR_POSITION = QName(KVG_NS, "position", KVG_PREFIX)
    private val ATTR_VARIANT = QName(KVG_NS, "variant", KVG_PREFIX)
    private val ATTR_PARTIAL = QName(KVG_NS, "partial", KVG_PREFIX)
    private val ATTR_PART = QName(KVG_NS, "part", KVG_PREFIX)
    private val ATTR_NUMBER = QName(KVG_NS, "number", KVG_PREFIX)
    private val ATTR_RADICAL = QName(KVG_NS, "radical", KVG_PREFIX)
    private val ATTR_PHON = QName(KVG_NS, "phon", KVG_PREFIX)
    private val ATTR_TRAD_FORM = QName(KVG_NS, "tradForm", KVG_PREFIX)
    private val ATTR_RADICAL_FORM = QName(KVG_NS, "radicalForm", KVG_PREFIX)

    fun parse(eventReader: XMLEventReader): KVGTag.SVG {
        eventReader.skip(
            XMLEvent.START_DOCUMENT, // <?xml ...
            XMLEvent.COMMENT, // <!-- Copyright ...
            XMLEvent.DTD, // <!DOCTYPE ...
            XMLEvent.SPACE
        )
        return svg(eventReader)
    }

    // UTILITY METHODS:

    private fun XMLEventReader.skip(vararg eventsToSkip: Int) {
        while (this.hasNext()) {
            if (eventsToSkip.contains(this.peek().eventType)) {
                this.nextEvent()
            } else {
                break
            }
        }
    }

    private fun XMLEventReader.skipSpace() = this.skip(XMLEvent.SPACE)

    private fun <E : XMLEvent> XMLEventReader.nextAs(clazz: Class<E>): E {
        try {
            val event = this.nextEvent() ?: throw NullPointerException("nextEvent() returned null")
            if ( ! clazz.isInstance(event)) {
                throw ParsingException.UnexpectedEvent(clazz, event)
            }
            @Suppress("UNCHECKED_CAST")
            return event as E
        } catch (e: NoSuchElementException) {
            throw ParsingException.UnexpectedEndOfDocument(e)
        }
    }

    private fun XMLEventReader.openTag(name: QName, description: String): StartElement {
        val event = this.nextAs(StartElement::class.java)
        if (event.asStartElement().name != name) {
            throw ParsingException.UnexpectedOpeningTag(name, description, event.asStartElement())
        }
        return event
    }

    private fun XMLEventReader.closeTag(name: QName, description: String): Unit {
        val event = this.nextAs(EndElement::class.java)
        if (event.asEndElement().name != TAG_SVG) {
            throw ParsingException.UnexpectedClosingTag(name, description, event.asEndElement())
        }
    }

    private fun <T> XMLEventReader.tag(name: QName, description: String, convert: (StartElement) -> T): T {
        val tag = this.openTag(name, description)
        val result = convert(tag)
        this.closeTag(name, description)
        return result
    }

    private fun <E : KVGTag> XMLEventReader.tagList(extractor: (XMLEventReader) -> E?): List<E> {
        val result = mutableListOf<E>()
        while (this.hasNext()) {
            this.skipSpace()
            result.add(extractor(this) ?: break)
        }
        return result.toList()
    }

    private fun <E : KVGTag> XMLEventReader.nonEmptyTagList(
        parentTag: StartElement,
        childTagName: QName,
        extractor: (XMLEventReader) -> E?
    ): List<E> {
        val result = this.tagList(extractor)
        if (result.isEmpty()) {
            throw ParsingException.EmptyChildrenList(parentTag, childTagName)
        }
        return result
    }

    private fun XMLEventReader.characters(parentTag: StartElement): Characters {
        val event = this.nextAs(Characters::class.java)
        if (event.isCData || event.isIgnorableWhiteSpace) {
            throw ParsingException.MissingCharacters(parentTag, event)
        }
        return event
    }

    private fun StartElement.requiredAttr(name: QName): Attribute {
        return this.getAttributeByName(name) ?:
            throw ParsingException.MissingRequiredAttribute(this, name)
    }

    private fun Attribute.toInt(context: StartElement): Int {
        try {
            return this.value.toInt()
        } catch (e: NumberFormatException) {
            throw ParsingException.InvalidAttributeFormat(context, this, "expected integer", e)
        }
    }

    private fun Attribute.toBoolean(context: StartElement): Boolean {
        try {
            return this.value.toBoolean()
        } catch (e: Exception) {
            throw ParsingException.InvalidAttributeFormat(context, this, "expected boolean", e)
        }
    }

    private fun <E : Enum<E>> Attribute.toEnum(context: StartElement, enumClass: Class<E>, values: Array<E>): E {
        try {
            return java.lang.Enum.valueOf(enumClass, this.value)
        } catch (e: Exception) {
            throw ParsingException.InvalidAttributeFormat(
                context, this,
                "expected one of these enum values: $values", e
            )
        }
    }

    // TAGS PARSING:

    private fun svg(eventReader: XMLEventReader): KVGTag.SVG {
        return eventReader.tag(TAG_SVG, "root tag") { tag ->
            val width = width(tag)
            val height = height(tag)
            val viewBox = viewBox(tag)
            eventReader.skipSpace()
            val strokePathsGroup = strokePathsGroup(eventReader)
            eventReader.skipSpace()
            val strokeNumbersGroup = strokeNumbersGroup(eventReader)
            eventReader.skipSpace()
            KVGTag.SVG(width, height, viewBox, strokePathsGroup, strokeNumbersGroup)
        }
    }

    private fun strokePathsGroup(eventReader: XMLEventReader): KVGTag.StrokePathsGroup {
        return eventReader.tag(TAG_GROUP, "stroke paths group") { tag ->
            val id = id(tag)
            val style = style(tag)
            eventReader.skipSpace()
            val rootGroup = strokeGroup(eventReader)
            eventReader.skipSpace()
            KVGTag.StrokePathsGroup(id, style, rootGroup)
        }
    }

    private fun strokeGroup(eventReader: XMLEventReader): KVGTag.StrokePathsSubGroup {
        return eventReader.tag(TAG_GROUP, "stroke group") { tag ->
            val id = id(tag)
            val element = tag.getAttributeByName(ATTR_ELEMENT)
                ?.value
                ?.let { Attr.KvgElement(it) }
            val original = tag.getAttributeByName(ATTR_ORIGINAL)
                ?.value
                ?.let { Attr.KvgOriginal(it) }
            val position = tag.getAttributeByName(ATTR_POSITION)
                ?.toEnum(tag, Attr.Position::class.java, Attr.Position.values())
                ?.let { Attr.KvgPosition(it) }
            val variant = tag.getAttributeByName(ATTR_VARIANT)
                ?.toBoolean(tag)
                ?.let { Attr.KvgVariant(it) }
            val partial = tag.getAttributeByName(ATTR_PARTIAL)
                ?.toBoolean(tag)
                ?.let { Attr.KvgPartial(it) }
            val part = tag.getAttributeByName(ATTR_PART)
                ?.toInt(tag)
                ?.let { Attr.KvgPart(it) }
            val number = tag.getAttributeByName(ATTR_NUMBER)
                ?.toInt(tag)
                ?.let { Attr.KvgNumber(it) }
            val radical = tag.getAttributeByName(ATTR_RADICAL)
                ?.toEnum(tag, Attr.Radical::class.java, Attr.Radical.values())
                ?.let { Attr.KvgRadical(it) }
            val phon = tag.getAttributeByName(ATTR_PHON)
                ?.value
                ?.let { Attr.KvgPhon(it) }
            val tradForm = tag.getAttributeByName(ATTR_TRAD_FORM)
                ?.value
                ?.let { Attr.KvgTradForm(it) }
            val radicalForm = tag.getAttributeByName(ATTR_RADICAL_FORM)
                ?.value
                ?.let { Attr.KvgRadicalForm(it) }

            eventReader.skipSpace()
            // val children = listOf<KVGTag.StrokePathsSubGroupChild>() // TODO
            eventReader.skipSpace()

            KVGTag.StrokePathsSubGroup(
                id = id,
                element = element,
                original = original,
                position = position,
                variant = variant,
                partial = partial,
                part = part,
                number = number,
                radical = radical,
                phon = phon,
                tradForm = tradForm,
                radicalForm = radicalForm,
                children = children
            )
        }
    }

    private fun strokeNumbersGroup(eventReader: XMLEventReader): KVGTag.StrokeNumbersGroup {
        return eventReader.tag(TAG_GROUP, "stroke numbers group") { tag ->
            val id = id(tag)
            val style = style(tag)
            eventReader.skipSpace()
            val children = eventReader.nonEmptyTagList(tag, TAG_TEXT, { strokeNumber(it) })
            eventReader.skipSpace()
            KVGTag.StrokeNumbersGroup(id, style, children)
        }
    }

    private fun strokeNumber(eventReader: XMLEventReader): KVGTag.StrokeNumber? {
        return eventReader.tag(TAG_TEXT, "stroke number") { tag ->
            val transform = transform(tag)
            val number = strokeNumberValue(tag, eventReader)
            KVGTag.StrokeNumber(number, transform)
        }
    }

    private fun strokeNumberValue(parentTag: StartElement, eventReader: XMLEventReader): Int {
        val characters = eventReader.characters(parentTag)
        try {
            return characters.data.trim().toInt()
        } catch (e: NumberFormatException) {
            throw ParsingException.InvalidCharactersFormat(
                parentTag, characters.data, "expected an integer", e
            )
        }
    }

    // ATTRIBUTES PARSING:

    private fun width(tag: StartElement): Attr.Width =
        Attr.Width(tag.requiredAttr(ATTR_WIDTH).toInt(tag))

    private fun height(tag: StartElement): Attr.Height =
        Attr.Height(tag.requiredAttr(ATTR_HEIGHT).toInt(tag))

    private fun viewBox(tag: StartElement): Attr.ViewBox {
        val attr = tag.requiredAttr(ATTR_VIEW_BOX)
        try {
            val parts = attr.value.trim().split(Regex("[,\\s]+"))
            if (parts.size != 4) {
                throw ParsingException.InvalidAttributeFormat(
                    tag, attr,
                    "there must be 4 integer parts in this element, separated by commas and/or whitespace"
                )
            }
            val x = parts[0].toInt()
            val y = parts[1].toInt()
            val w = parts[2].toInt()
            val h = parts[3].toInt()
            if (w < 0) {
                throw ParsingException.InvalidAttributeFormat(
                    tag, attr,
                    "width (3rd element) cannot be less than 0"
                )
            }
            if (h < 0) {
                throw ParsingException.InvalidAttributeFormat(
                    tag, attr,
                    "height (4th element) cannot be less than 0"
                )
            }
            return Attr.ViewBox(x, y, w, h)
        } catch (e: NumberFormatException) {
            throw ParsingException.InvalidAttributeFormat(
                tag, attr,
                e.message ?: "invalid number format", e
            )
        }
    }

    private fun id(element: StartElement): Attr.Id {
        return Attr.Id(element.requiredAttr(ATTR_ID).value)
    }

    private fun style(element: StartElement): Attr.Style {
        val attr = element.requiredAttr(ATTR_STYLE)
        val parts = attr.value.trim().split(Regex("[;\\s]+")).map { part ->
            val pair = part.split(Regex("[:\\s]+"))
            if (pair.size != 2) {
                throw ParsingException.InvalidAttributeFormat(
                    element, attr,
                    "the '$part' part must have a format of 'key: value'"
                )
            }
            Pair(pair[0], pair[1])
        }.toMap()
        return Attr.Style(parts)
    }

    private fun transform(element: StartElement): Attr.Transform {
        val attr = element.requiredAttr(ATTR_TRANSFORM)
        val rawString = attr.value.trim()
        if ( ! rawString.matches(Regex("^matrix(.*)$"))) {
            throw ParsingException.InvalidAttributeFormat(element, attr, "expected 'matrix(...)'")
        }
        val numbers = rawString.split("(")[1].split(")")[0].trim().split(Regex("[,\\s]+"))
        if (numbers.size != 6) {
            throw ParsingException.InvalidAttributeFormat(
                element, attr,
                "expected exactly 6 elements inside 'matrix(...)'"
            )
        }
        try {
            return Attr.Transform(
                Attr.Transform.Matrix(numbers.map { it.toDouble() })
            )
        } catch (e: NumberFormatException) {
            throw ParsingException.InvalidAttributeFormat(
                element, attr,
                "expected only numeric values inside 'matrix(...)'"
            )
        }
    }
}
