package org.kanjivg.tools.parsing

import javax.xml.namespace.QName
import javax.xml.stream.events.Attribute
import javax.xml.stream.events.EndElement
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent

sealed class ParsingException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)

    class UnexpectedEndOfDocument(cause: Throwable) : ParsingException("Unexpected end of document", cause)

    companion object {
        private fun replaceWS(s: String): String {
            return s.replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }

        protected fun show(event: XMLEvent): String {
            return when (event.eventType) {
                XMLEvent.SPACE -> "[whitespace]"
                XMLEvent.CHARACTERS -> "[characters:'${replaceWS(event.asCharacters().data)}']"
                else -> event.toString()
            }
        }
    }

    class UnexpectedEvent : ParsingException {
        constructor(expectedEventType: Class<*>, unexpectedEvent: XMLEvent, description: String) :
            super("Expected event of type ${expectedEventType.simpleName} ($description), " +
                "got ${show(unexpectedEvent)}")
        constructor(expectedEventTypes: List<Class<*>>, unexpectedEvent: XMLEvent, description: String) :
            super("Expected one of these event types: ${expectedEventTypes.map { it.simpleName }} ($description), " +
                "got ${show(unexpectedEvent)}")
    }

    class UnexpectedOpeningTag : ParsingException {
        constructor(expectedTag: QName, description: String, unexpectedTag: StartElement) :
            super("Expected opening tag <$expectedTag> ($description), got $unexpectedTag")
        constructor(expectedTags: List<QName>, description: String, unexpectedTag: StartElement) :
            super("Expected opening tag ${expectedTags.map { "<$it>" }} ($description), got $unexpectedTag")
    }
    class UnexpectedClosingTag(expectedTag: QName, description: String, unexpectedTag: EndElement) :
        ParsingException("Expected closing tag </$expectedTag> ($description), got $unexpectedTag")

    class InvalidAttributeFormat(
        tag: StartElement,
        attribute: Attribute,
        details: String,
        cause: Throwable? = null
    ) : ParsingException("Invalid format of an attribute [$attribute] in tag $tag: $details", cause)

    class MissingRequiredAttribute(tag: StartElement, attributeName: QName) :
        ParsingException("Missing required attribute [$attributeName] in tag $tag")

    class ProhibitedAttributes(tag: StartElement, attributeNames: List<String>, allowedAttributes: List<String>) :
        ParsingException("Prohibited attributes $attributeNames in tag $tag, allowed attributes are $allowedAttributes")

    class EmptyChildrenList(parentTag: StartElement, expectedChildTag: QName) :
        ParsingException("Expected at least one child tag <$expectedChildTag> in tag $parentTag")

    class MissingCharacters(parentTag: StartElement, unexpectedEvent: XMLEvent) :
        ParsingException("Expected characters inside $parentTag, got $unexpectedEvent")

    class InvalidCharactersFormat(
        parentTag: StartElement,
        text: String,
        details: String,
        cause: Throwable? = null
    ) : ParsingException("Invalid format of a text '$text' in tag $parentTag: $details", cause)
}
