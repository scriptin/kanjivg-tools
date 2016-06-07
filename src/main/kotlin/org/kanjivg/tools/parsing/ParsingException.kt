package org.kanjivg.tools.parsing

import javax.xml.namespace.QName
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.EndElement
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent

sealed class ParsingException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)

    class UnexpectedEndOfDocumentException(cause: Throwable) : ParsingException("Unexpected end of document", cause)

    class MissingOpenTagException(expectedTag: QName, description: String, unexpectedEvent: XMLEvent) :
        ParsingException("Expected opening tag <$expectedTag> ($description), got $unexpectedEvent")
    class MissingCloseTagException(expectedTag: QName, description: String, unexpectedEvent: XMLEvent) :
        ParsingException("Expected closing tag </$expectedTag> ($description), got $unexpectedEvent")

    class UnexpectedOpenTagException(expectedTag: QName, description: String, unexpectedTag: StartElement) :
        ParsingException("Expected <$expectedTag> ($description), got $unexpectedTag")
    class UnexpectedCloseTagException(expectedTag: QName, description: String, unexpectedTag: EndElement) :
        ParsingException("Expected </$expectedTag> ($description), got $unexpectedTag")

    class AttributeFormatException(
        tag: StartElement,
        attribute: Attribute,
        details: String,
        cause: Throwable? = null // null cause is allowed in RuntimeException
    ) : ParsingException("Wrong format of an attribute $attribute in tag $tag: $details", cause)

    class MissingRequiredAttributeException(tag: StartElement, attributeName: QName) :
        ParsingException("Missing required attribute $attributeName in tag $tag")

    class EmptyChildrenListException(parentTag: StartElement, expectedChildTag: QName) :
        ParsingException("Expected at least one child tag <$expectedChildTag> in tag $parentTag")

    class MissingCharactersException(parentTag: StartElement, unexpectedEvent: XMLEvent) :
        ParsingException("Expected characters inside $parentTag, got $unexpectedEvent")

    class CharactersFormatException(
        parentTag: StartElement,
        text: String,
        details: String,
        cause: Throwable? = null
    ) : ParsingException("Wrong format of a text $text in tag $parentTag: $details", cause)
}
