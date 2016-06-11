package org.kanjivg.tools.parsing

import org.kanjivg.tools.KVGTag
import java.util.*
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.*
import org.kanjivg.tools.KVGTag.Attribute as Attr

object KanjiSVGParser {
    private const val SVG_NS = "http://www.w3.org/2000/svg"
    private const val KVG_NS = "http://kanjivg.tagaini.net"
    private const val KVG_PREFIX = "kvg"

    private val TAG_SVG = QName("svg")
    private val TAG_GROUP = QName("g")
    private val TAG_PATH = QName("path")
    private val TAG_TEXT = QName("text")

    private val ATTR_ID = QName("id")
    private val ATTR_WIDTH = QName("width")
    private val ATTR_HEIGHT = QName("height")
    private val ATTR_VIEW_BOX = QName("viewBox")
    private val ATTR_STYLE = QName("style")
    private val ATTR_TRANSFORM = QName("transform")
    private val ATTR_PATH = QName("d")

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
    private val ATTR_TYPE = QName(KVG_NS, "type", KVG_PREFIX)

    private val REGEX_MATRIX = Regex("^matrix(.*)$")
    private val REGEX_COMMA_OR_SPACE = Regex("[,\\s]+")
    private val REGEX_COLON_OR_SPACE = Regex("[:\\s]+")
    private val REGEX_SEMICOLON_OR_SPACE = Regex("[;\\s]+")

    fun parse(eventReader: XMLEventReader): KVGTag.SVG {
        eventReader.skip(setOf(
            XMLEvent.START_DOCUMENT, // <?xml ...
            XMLEvent.COMMENT, // <!-- Copyright ...
            XMLEvent.DTD, // <!DOCTYPE ...
            XMLEvent.SPACE
        ))
        return svg(eventReader)
    }

    // UTILITY METHODS:

    private fun XMLEventReader.skip(eventsToSkip: Set<Int>) {
        if (eventsToSkip.isEmpty()) return
        while (hasNext()) {
            if (eventsToSkip.contains(peek().eventType)) {
                nextEvent()
            } else {
                return
            }
        }
    }

    private fun XMLEventReader.skipSpace() {
        while (hasNext()) {
            val event = peek()
            when (event.eventType) {
                XMLEvent.SPACE -> nextEvent()
                XMLEvent.CHARACTERS -> if (event.asCharacters().data.isBlank()) nextEvent() else return
                else -> return
            }
        }
    }

    private fun <E : XMLEvent> XMLEventReader.nextAs(clazz: Class<E>, description: String): E {
        try {
            val event = nextEvent() ?: throw NullPointerException("nextEvent() returned null")
            if ( ! clazz.isInstance(event)) {
                throw ParsingException.UnexpectedEvent(clazz, event, description)
            }
            @Suppress("UNCHECKED_CAST")
            return event as E
        } catch (e: NoSuchElementException) {
            throw ParsingException.UnexpectedEndOfDocument(e)
        }
    }

    private fun XMLEventReader.openTag(name: QName, description: String): StartElement {
        skipSpace()
        val event = nextAs(StartElement::class.java, "opening tag <$name>")
        if (event.asStartElement().name != name) {
            throw ParsingException.UnexpectedOpeningTag(name, description, event.asStartElement())
        }
        skipSpace()
        return event
    }

    private fun XMLEventReader.closeTag(name: QName, description: String): Unit {
        skipSpace()
        val event = nextAs(EndElement::class.java, "closing tag </$name>")
        if (event.asEndElement().name != name) {
            throw ParsingException.UnexpectedClosingTag(name, description, event.asEndElement())
        }
        skipSpace()
    }

    private fun <T> XMLEventReader.tag(name: QName, description: String, convert: (StartElement) -> T): T {
        val tag = openTag(name, description)
        val result = convert(tag)
        closeTag(name, description)
        return result
    }

    private fun <T : KVGTag> XMLEventReader.tagList(extractor: (XMLEventReader) -> T?): List<T> {
        val result = mutableListOf<T>()
        while (hasNext()) {
            skipSpace()
            if (peek().isEndElement) break
            result.add(extractor(this) ?: break)
        }
        skipSpace()
        return result.toList()
    }

    private fun <T : KVGTag> XMLEventReader.nonEmptyTagList(
        parentTag: StartElement,
        childTagName: QName,
        extractor: (XMLEventReader) -> T?
    ): List<T> {
        val result = tagList(extractor)
        if (result.isEmpty()) {
            throw ParsingException.EmptyChildrenList(parentTag, childTagName)
        }
        return result
    }

    private fun <T> XMLEventReader.tagList(parser: (StartElement, XMLEventReader) -> T): List<T> {
        val result = mutableListOf<T>()
        loop@ while (hasNext()) {
            skipSpace()
            val nextEvent = peek()
            when (nextEvent) {
                is EndElement -> break@loop
                is StartElement -> parser(nextEvent, this)
                else -> throw ParsingException.UnexpectedEvent(
                    listOf(StartElement::class.java, EndElement::class.java),
                    nextEvent,
                    "closing parent tag or opening child tag"
                )
            }
        }
        skipSpace()
        return result.toList()
    }

    private fun XMLEventReader.characters(parentTag: StartElement): Characters {
        val event = nextAs(Characters::class.java, "characters inside $parentTag")
        if (event.isCData || event.isIgnorableWhiteSpace) {
            throw ParsingException.MissingCharacters(parentTag, event)
        }
        return event
    }

    private fun StartElement.requiredAttr(name: QName): Attribute {
        return getAttributeByName(name) ?:
            throw ParsingException.MissingRequiredAttribute(this, name)
    }

    private fun StartElement.attrString(name: QName): String? = getAttributeByName(name)?.value

    private fun StartElement.attrInt(name: QName): Int? = getAttributeByName(name)?.toInt(this)

    private fun StartElement.attrBoolean(name: QName): Boolean? = getAttributeByName(name)?.toBoolean(this)

    private fun <E : Enum<E>> StartElement.attrEnum(name: QName, enumClass: Class<E>): E? =
        getAttributeByName(name)?.toEnum(this, enumClass)

    private fun Attribute.toInt(context: StartElement): Int {
        try {
            return value.toInt()
        } catch (e: NumberFormatException) {
            throw ParsingException.InvalidAttributeFormat(context, this, "expected integer", e)
        }
    }

    private fun Attribute.toBoolean(context: StartElement): Boolean {
        try {
            return value.toBoolean()
        } catch (e: Exception) {
            throw ParsingException.InvalidAttributeFormat(context, this, "expected boolean", e)
        }
    }

    private fun <E : Enum<E>> Attribute.toEnum(context: StartElement, enumClass: Class<E>): E {
        try {
            return java.lang.Enum.valueOf(enumClass, value)
        } catch (e: Exception) {
            throw ParsingException.InvalidAttributeFormat(
                context, this,
                "expected one of these enum values: ${enumClass.enumConstants}", e
            )
        }
    }

    // TAGS PARSING:

    private fun svg(eventReader: XMLEventReader): KVGTag.SVG {
        return eventReader.tag(TAG_SVG, "root tag") { tag ->
            KVGTag.SVG(
                width = Attr.Width(tag.requiredAttr(ATTR_WIDTH).toInt(tag)),
                height = Attr.Height(tag.requiredAttr(ATTR_HEIGHT).toInt(tag)),
                viewBox = viewBox(tag),
                strokePathsGroup = strokePathsGroup(eventReader),
                strokeNumbersGroup = strokeNumbersGroup(eventReader)
            )
        }
    }

    private fun strokePathsGroup(eventReader: XMLEventReader): KVGTag.StrokePathsGroup {
        return eventReader.tag(TAG_GROUP, "stroke paths group") { tag ->
            KVGTag.StrokePathsGroup(
                id = id(tag),
                style = style(tag),
                rootGroup = strokeGroup(eventReader)
            )
        }
    }

    private fun strokeGroup(eventReader: XMLEventReader): KVGTag.StrokePathsSubGroup {
        return eventReader.tag(TAG_GROUP, "stroke group") { tag ->
            KVGTag.StrokePathsSubGroup(
                id = id(tag),
                element = tag.attrString(ATTR_ELEMENT)?.let { Attr.KvgElement(it) },
                original = tag.attrString(ATTR_ORIGINAL)?.let { Attr.KvgOriginal(it) },
                position = tag.attrEnum(ATTR_POSITION, Attr.Position::class.java)?.let { Attr.KvgPosition(it) },
                variant = tag.attrBoolean(ATTR_VARIANT)?.let { Attr.KvgVariant(it) },
                partial = tag.attrBoolean(ATTR_PARTIAL)?.let { Attr.KvgPartial(it) },
                part = tag.attrInt(ATTR_PART)?.let { Attr.KvgPart(it) },
                number = tag.attrInt(ATTR_NUMBER)?.let { Attr.KvgNumber(it) },
                radical = tag.attrEnum(ATTR_RADICAL, Attr.Radical::class.java)?.let { Attr.KvgRadical(it) },
                phon = tag.attrString(ATTR_PHON)?.let { Attr.KvgPhon(it) },
                tradForm = tag.attrString(ATTR_TRAD_FORM)?.let { Attr.KvgTradForm(it) },
                radicalForm = tag.attrString(ATTR_RADICAL_FORM)?.let { Attr.KvgRadicalForm(it) },
                children = eventReader.tagList<KVGTag.StrokePathsSubGroupChild> { tag, eventReader ->
                    when (tag.name) {
                        TAG_GROUP -> strokeGroup(eventReader)
                        TAG_PATH -> stroke(eventReader)
                        else -> throw ParsingException.UnexpectedOpeningTag(
                            listOf(TAG_GROUP, TAG_PATH), "stroke group or stroke", tag
                        )
                    }
                }
            )
        }
    }

    private fun stroke(eventReader: XMLEventReader): KVGTag.Path {
        return eventReader.tag(TAG_PATH, "stroke path") { tag ->
            KVGTag.Path(
                id = id(tag),
                type = tag.attrString(ATTR_TYPE)?.let { Attr.KvgType(it) },
                path = Attr.Path(tag.requiredAttr(ATTR_PATH).value)
            )
        }
    }

    private fun strokeNumbersGroup(eventReader: XMLEventReader): KVGTag.StrokeNumbersGroup {
        return eventReader.tag(TAG_GROUP, "stroke numbers group") { tag ->
            KVGTag.StrokeNumbersGroup(
                id = id(tag),
                style = style(tag),
                children = eventReader.nonEmptyTagList(tag, TAG_TEXT, { strokeNumber(it) })
            )
        }
    }

    private fun strokeNumber(eventReader: XMLEventReader): KVGTag.StrokeNumber? {
        return eventReader.tag(TAG_TEXT, "stroke number") { tag ->
            KVGTag.StrokeNumber(
                transform = transform(tag),
                value = strokeNumberValue(tag, eventReader)
            )
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

    private fun id(element: StartElement): Attr.Id = Attr.Id(element.requiredAttr(ATTR_ID).value)

    private fun viewBox(tag: StartElement): Attr.ViewBox {
        val attr = tag.requiredAttr(ATTR_VIEW_BOX)
        try {
            val parts = attr.value.trim().split(REGEX_COMMA_OR_SPACE)
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

    private fun style(element: StartElement): Attr.Style {
        val attr = element.requiredAttr(ATTR_STYLE)
        val parts = attr.value.trim().split(REGEX_SEMICOLON_OR_SPACE).filter { ! it.isBlank() }.map { part ->
            val pair = part.split(REGEX_COLON_OR_SPACE)
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
        if ( ! rawString.matches(REGEX_MATRIX)) {
            throw ParsingException.InvalidAttributeFormat(element, attr, "expected 'matrix(...)'")
        }
        val numbers = rawString.split("(")[1].split(")")[0].trim().split(REGEX_COMMA_OR_SPACE)
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
