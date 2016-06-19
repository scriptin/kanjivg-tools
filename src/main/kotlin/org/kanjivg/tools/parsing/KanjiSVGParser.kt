package org.kanjivg.tools.parsing

import org.kanjivg.tools.KVGTag
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.*
import org.kanjivg.tools.KVGTag.Attribute as Attr

/**
 * Parser of KanjiVG SVG files based on StAX iterator API.
 *
 * This parser not only checks if SVG files are valid and well-formed (this is done by underlying StAX API),
 * but also checks some KanjiVG-specific "structural" features. For example, it makes sure that
 * a group (<g> tag) of stroke numbers only contains <text> elements with numbers inside them.
 */
object KanjiSVGParser {
    private const val KVG_PREFIX = "kvg"

    private val TAG_SVG = QName("svg")
    private val TAG_GROUP = QName("g")
    private val TAG_PATH = QName("path")
    private val TAG_TEXT = QName("text")

    private val ATTR_XMLNS = QName("xmlns")
    private val ATTR_ID = QName("id")
    private val ATTR_WIDTH = QName("width")
    private val ATTR_HEIGHT = QName("height")
    private val ATTR_VIEW_BOX = QName("viewBox")
    private val ATTR_STYLE = QName("style")
    private val ATTR_TRANSFORM = QName("transform")
    private val ATTR_PATH = QName("d")

    private val ATTR_ELEMENT = QName(null, "element", KVG_PREFIX)
    private val ATTR_ORIGINAL = QName(null, "original", KVG_PREFIX)
    private val ATTR_POSITION = QName(null, "position", KVG_PREFIX)
    private val ATTR_VARIANT = QName(null, "variant", KVG_PREFIX)
    private val ATTR_PARTIAL = QName(null, "partial", KVG_PREFIX)
    private val ATTR_PART = QName(null, "part", KVG_PREFIX)
    private val ATTR_NUMBER = QName(null, "number", KVG_PREFIX)
    private val ATTR_RADICAL = QName(null, "radical", KVG_PREFIX)
    private val ATTR_PHON = QName(null, "phon", KVG_PREFIX)
    private val ATTR_TRAD_FORM = QName(null, "tradForm", KVG_PREFIX)
    private val ATTR_RADICAL_FORM = QName(null, "radicalForm", KVG_PREFIX)
    private val ATTR_TYPE = QName(null, "type", KVG_PREFIX)

    private val REGEX_MATRIX = Regex("^matrix(.*)$")
    private val REGEX_COMMA_OR_SPACE = Regex("[,\\s]+")
    private val REGEX_COLON_OR_SPACE = Regex("[:\\s]+")
    private val REGEX_SEMICOLON_OR_SPACE = Regex("[;\\s]+")

    /**
     * Parse an XML file given as an event reader and return a domain object.
     * All syntactic as well as "structural" errors will produce exceptions.
     * @throws [ParsingException]
     */
    fun parse(eventReader: XMLEventReader): KVGTag.SVG {
        eventReader.skip(setOf(
            XMLEvent.START_DOCUMENT, // <?xml ...
            XMLEvent.COMMENT, // <!-- Copyright ...
            XMLEvent.DTD, // <!DOCTYPE ...
            XMLEvent.SPACE
        ))
        return svg(eventReader)
    }

    // TAGS PARSING:

    private fun svg(eventReader: XMLEventReader): KVGTag.SVG {
        return eventReader.tag(TAG_SVG, "root tag") { tag ->
            tag.checkAllowedAttributes(setOf(
                ATTR_XMLNS,
                ATTR_WIDTH, ATTR_HEIGHT,
                ATTR_VIEW_BOX
            ))
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
            tag.checkAllowedAttributes(setOf(ATTR_ID, ATTR_STYLE, ATTR_ELEMENT))
            KVGTag.StrokePathsGroup(
                id = id(tag),
                style = style(tag),
                rootGroup = strokeGroup(eventReader)
            )
        }
    }

    private fun strokeGroup(eventReader: XMLEventReader): KVGTag.StrokePathsSubGroup {
        return eventReader.tag(TAG_GROUP, "stroke group") { tag ->
            tag.checkAllowedAttributes(setOf(
                ATTR_ID,
                ATTR_ELEMENT,
                ATTR_ORIGINAL,
                ATTR_POSITION,
                ATTR_VARIANT,
                ATTR_PARTIAL,
                ATTR_PART,
                ATTR_NUMBER,
                ATTR_RADICAL,
                ATTR_PHON,
                ATTR_TRAD_FORM,
                ATTR_RADICAL_FORM
            ))
            KVGTag.StrokePathsSubGroup(
                id = id(tag),
                element = tag.attrString(ATTR_ELEMENT)?.let { Attr.KvgElement(it) },
                original = tag.attrString(ATTR_ORIGINAL)?.let { Attr.KvgOriginal(it) },
                position = tag.attrEnum(
                    ATTR_POSITION,
                    Attr.Position.values(),
                    { Attr.Position.fromString(it) }
                )?.let { Attr.KvgPosition(it) },
                variant = tag.attrBoolean(ATTR_VARIANT)?.let { Attr.KvgVariant(it) },
                partial = tag.attrBoolean(ATTR_PARTIAL)?.let { Attr.KvgPartial(it) },
                part = tag.attrInt(ATTR_PART)?.let { Attr.KvgPart(it) },
                number = tag.attrInt(ATTR_NUMBER)?.let { Attr.KvgNumber(it) },
                radical = tag.attrEnum(
                    ATTR_RADICAL,
                    Attr.Radical.values(),
                    { Attr.Radical.fromString(it) }
                )?.let { Attr.KvgRadical(it) },
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
            tag.checkAllowedAttributes(setOf(ATTR_ID, ATTR_PATH, ATTR_TYPE))
            KVGTag.Path(
                id = id(tag),
                type = tag.attrString(ATTR_TYPE)?.let { Attr.KvgType(it) },
                path = Attr.Path(tag.requiredAttr(ATTR_PATH).value)
            )
        }
    }

    private fun strokeNumbersGroup(eventReader: XMLEventReader): KVGTag.StrokeNumbersGroup {
        return eventReader.tag(TAG_GROUP, "stroke numbers group") { tag ->
            tag.checkAllowedAttributes(setOf(ATTR_ID, ATTR_STYLE))
            KVGTag.StrokeNumbersGroup(
                id = id(tag),
                style = style(tag),
                children = eventReader.nonEmptyTagList(tag, TAG_TEXT, { strokeNumber(it) })
            )
        }
    }

    private fun strokeNumber(eventReader: XMLEventReader): KVGTag.StrokeNumber? {
        return eventReader.tag(TAG_TEXT, "stroke number") { tag ->
            tag.checkAllowedAttributes(setOf(ATTR_TRANSFORM))
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
