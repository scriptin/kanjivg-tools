package org.kanjivg.tools.parsing

import org.kanjivg.tools.KVGTag
import java.util.*
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.*

/**
 * Skip events which are not important
 */
internal fun XMLEventReader.skip(eventsToSkip: Set<Int>) {
    if (eventsToSkip.isEmpty()) return
    while (hasNext()) {
        if (eventsToSkip.contains(peek().eventType)) {
            nextEvent()
        } else {
            return
        }
    }
}

/**
 * Skip any whitespace: [XMLEvent].SPACE and [XMLEvent].CHARACTERS (if contains only whitespace characters)
 */
internal fun XMLEventReader.skipSpace() {
    while (hasNext()) {
        val event = peek()
        when (event.eventType) {
            XMLEvent.SPACE -> nextEvent()
            XMLEvent.CHARACTERS -> if (event.asCharacters().data.isBlank()) nextEvent() else return
            else -> return
        }
    }
}

/**
 * Get next event as a specific subclass of [XMLEvent]
 */
internal fun <E : XMLEvent> XMLEventReader.nextAs(clazz: Class<E>, description: String): E {
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

/**
 * Read opening tag event
 */
internal fun XMLEventReader.openTag(name: QName, description: String): StartElement {
    skipSpace()
    val event = nextAs(StartElement::class.java, "opening tag <$name>")
    if (event.asStartElement().name != name) {
        throw ParsingException.UnexpectedOpeningTag(name, description, event.asStartElement())
    }
    skipSpace()
    return event
}

/**
 * Read closing tag event
 */
internal fun XMLEventReader.closeTag(name: QName, description: String): Unit {
    skipSpace()
    val event = nextAs(EndElement::class.java, "closing tag </$name>")
    if (event.asEndElement().name != name) {
        throw ParsingException.UnexpectedClosingTag(name, description, event.asEndElement())
    }
    skipSpace()
}

/**
 * Read whole tag (opening and closing) given a lambda to parse its' attributes and body
 */
internal fun <T> XMLEventReader.tag(name: QName, description: String, convert: (StartElement) -> T): T {
    val tag = openTag(name, description)
    val result = convert(tag)
    closeTag(name, description)
    return result
}

/**
 * Read a homogeneous list of child tags
 */
internal fun <T : KVGTag> XMLEventReader.tagList(extractor: (XMLEventReader) -> T?): List<T> {
    val result = mutableListOf<T>()
    while (hasNext()) {
        skipSpace()
        if (peek().isEndElement) break
        result.add(extractor(this) ?: break)
    }
    skipSpace()
    return result.toList()
}

/**
 * Read a homogeneous list of child tags and make sure it's not empty (throw exceptions otherwise)
 */
internal fun <T : KVGTag> XMLEventReader.nonEmptyTagList(
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

/**
 * Read a heterogeneous list of child tags
 *
 * NB: [T] is not a subtype of [KVGTag] because this function has to return a list of elements of different types,
 * thus they may only have a common interface or be of some subtype of [KVGTag] in a best case.
 * Given lambda has to deal with types appropriately
 */
internal fun <T> XMLEventReader.tagList(parser: (StartElement, XMLEventReader) -> T): List<T> {
    val result = mutableListOf<T>()
    loop@ while (hasNext()) {
        skipSpace()
        val nextEvent = peek()
        when (nextEvent) {
            is EndElement -> break@loop
            is StartElement -> result.add(parser(nextEvent, this))
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

/**
 * Read characters event
 */
internal fun XMLEventReader.characters(parentTag: StartElement): Characters {
    val event = nextAs(Characters::class.java, "characters inside $parentTag")
    if (event.isCData || event.isIgnorableWhiteSpace) {
        throw ParsingException.MissingCharacters(parentTag, event)
    }
    return event
}

/**
 * Convert [QName] into a [String] of 'prefix:localName' or 'localName' format
 *
 * NB: Namespace is simply ignored, which is the opposite of equals() methods of [QName],
 * which compares namespace and localPart, ignoring a prefix
 */
internal fun QName.toPlainString(): String {
    return "${prefix?.let { if (it.isEmpty()) "" else "$it:" } ?: ""}$localPart"
}

/**
 * Check if a tag only has attributes from a given set, throw an exception if some extra attributes are present,
 * but treat elements of this set as optional (i.e. allow missing attributes)
 */
internal fun StartElement.checkAllowedAttributes(attrs: Set<QName>): Unit {
    val allowedAttrs = attrs.map { it.toPlainString() }
    val actualAttrs = attributes.asSequence().map { attr ->
        when (attr) {
            is Attribute -> attr.name.toPlainString()
            else -> throw RuntimeException(
                "Found an object [${attr.toString()}] in attributes of element $this, " +
                    "not an instance of ${Attribute::class.java.name}"
            )
        }
    }.toList()
    val prohibitedAttrs = actualAttrs.filter { !allowedAttrs.contains(it) }
    if (prohibitedAttrs.isNotEmpty()) {
        throw ParsingException.ProhibitedAttributes(this, prohibitedAttrs, allowedAttrs)
    }
}

/**
 * Get an attribute or throw an exception
 */
internal fun StartElement.requiredAttr(name: QName): Attribute {
    return getAttributeByName(name) ?:
        throw ParsingException.MissingRequiredAttribute(this, name)
}

/**
 * Get an attribute value as an optional [String]
 */
internal fun StartElement.attrString(name: QName): String? = getAttributeByName(name)?.value

/**
 * Get an attribute value as an optional [Int]
 */
internal fun StartElement.attrInt(name: QName): Int? = getAttributeByName(name)?.toInt(this)

/**
 * Get an attribute value as an optional [Boolean]
 */
internal fun StartElement.attrBoolean(name: QName): Boolean? = getAttributeByName(name)?.toBoolean(this)

/**
 * Get an attribute value as an optional [Enum]
 */
internal fun <E : Enum<E>> StartElement.attrEnum(name: QName, values: Array<E>, fromString: (String) -> E): E? =
    getAttributeByName(name)?.toEnum(this, values, fromString)

/**
 * Convert an attribute value to [Int]
 */
internal fun Attribute.toInt(context: StartElement): Int {
    try {
        return value.toInt()
    } catch (e: NumberFormatException) {
        throw ParsingException.InvalidAttributeFormat(context, this, "expected integer", e)
    }
}

/**
 * Convert an attribute value to [Boolean]
 */
internal fun Attribute.toBoolean(context: StartElement): Boolean {
    try {
        return value.toBoolean()
    } catch (e: Exception) {
        throw ParsingException.InvalidAttributeFormat(context, this, "expected boolean", e)
    }
}

/**
 * Convert an attribute value to [Enum]
 */
internal fun <E : Enum<E>> Attribute.toEnum(context: StartElement, values: Array<E>, fromString: (String) -> E): E {
    try {
        return fromString(value)
    } catch (e: Exception) {
        throw ParsingException.InvalidAttributeFormat(
            context, this, "expected one of these enum values: $values", e
        )
    }
}
