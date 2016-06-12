package org.kanjivg.tools.parsing

import org.kanjivg.tools.KVGTag
import java.util.*
import javax.xml.namespace.QName
import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.*

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

internal fun XMLEventReader.openTag(name: QName, description: String): StartElement {
    skipSpace()
    val event = nextAs(StartElement::class.java, "opening tag <$name>")
    if (event.asStartElement().name != name) {
        throw ParsingException.UnexpectedOpeningTag(name, description, event.asStartElement())
    }
    skipSpace()
    return event
}

internal fun XMLEventReader.closeTag(name: QName, description: String): Unit {
    skipSpace()
    val event = nextAs(EndElement::class.java, "closing tag </$name>")
    if (event.asEndElement().name != name) {
        throw ParsingException.UnexpectedClosingTag(name, description, event.asEndElement())
    }
    skipSpace()
}

internal fun <T> XMLEventReader.tag(name: QName, description: String, convert: (StartElement) -> T): T {
    val tag = openTag(name, description)
    val result = convert(tag)
    closeTag(name, description)
    return result
}

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

internal fun XMLEventReader.characters(parentTag: StartElement): Characters {
    val event = nextAs(Characters::class.java, "characters inside $parentTag")
    if (event.isCData || event.isIgnorableWhiteSpace) {
        throw ParsingException.MissingCharacters(parentTag, event)
    }
    return event
}

internal fun QName.toPlainString(): String {
    return "${prefix?.let { if (it.isEmpty()) "" else "$it:" } ?: ""}$localPart"
}

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

internal fun StartElement.requiredAttr(name: QName): Attribute {
    return getAttributeByName(name) ?:
        throw ParsingException.MissingRequiredAttribute(this, name)
}

internal fun StartElement.attrString(name: QName): String? = getAttributeByName(name)?.value

internal fun StartElement.attrInt(name: QName): Int? = getAttributeByName(name)?.toInt(this)

internal fun StartElement.attrBoolean(name: QName): Boolean? = getAttributeByName(name)?.toBoolean(this)

internal fun <E : Enum<E>> StartElement.attrEnum(name: QName, enumClass: Class<E>): E? =
    getAttributeByName(name)?.toEnum(this, enumClass)

internal fun Attribute.toInt(context: StartElement): Int {
    try {
        return value.toInt()
    } catch (e: NumberFormatException) {
        throw ParsingException.InvalidAttributeFormat(context, this, "expected integer", e)
    }
}

internal fun Attribute.toBoolean(context: StartElement): Boolean {
    try {
        return value.toBoolean()
    } catch (e: Exception) {
        throw ParsingException.InvalidAttributeFormat(context, this, "expected boolean", e)
    }
}

internal fun <E : Enum<E>> Attribute.toEnum(context: StartElement, enumClass: Class<E>): E {
    try {
        return java.lang.Enum.valueOf(enumClass, value)
    } catch (e: Exception) {
        throw ParsingException.InvalidAttributeFormat(
            context, this,
            "expected one of these enum values: ${enumClass.enumConstants}", e
        )
    }
}
