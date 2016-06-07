package org.kanjivg.tools.parsing

/** SVG tags */
sealed class KVGTag(open val name: String) {
    /** Root <svg> element */
    class SVG(
        val width: Attribute.Width,
        val height: Attribute.Height,
        val viewBox: Attribute.ViewBox,
        val strokePathsGroup: StrokePathsGroup,
        val strokeNumbersGroup: StrokeNumbersGroup
    ) : KVGTag("svg")

    /** SVG group element, <g>. NB: group itself can ba a child of another group */
    abstract class Group(open val id: Attribute.Id) : KVGTag("group")

    class StrokePathsGroup(
        override val id: Attribute.Id,
        val style: Attribute.Style,
        val children: List<StrokePathsGroupChild>
    ) : Group(id)

    interface StrokePathsGroupChild

    class StrokePathsSubGroup(
        override val id: Attribute.Id,
        val element: Attribute.KvgElement?,
        val original: Attribute.KvgOriginal?,
        val position: Attribute.KvgPosition?,
        val variant: Attribute.KvgVariant?,
        val partial: Attribute.KvgPartial?,
        val part: Attribute.KvgPart?,
        val number: Attribute.KvgNumber?,
        val radical: Attribute.KvgRadical?,
        val phon: Attribute.KvgPhon?,
        val tradForm: Attribute.KvgTradForm?,
        val radicalForm: Attribute.KvgRadicalForm?,
        val children: List<StrokePathsGroupChild>
    ) : Group(id), StrokePathsGroupChild

    /** SVG path element, <path> */
    class Path(
        val id: Attribute.Id,
        val type: Attribute.KvgType,
        val path: String // TODO: add proper type, aware of SVG path string format
    ) : KVGTag("path"), StrokePathsGroupChild

    class StrokeNumbersGroup(
        override val id: Attribute.Id,
        val style: Attribute.Style,
        val children: List<StrokeNumber>
    ) : Group(id)

    /** SVG <text> element with a stroke number */
    class StrokeNumber(val value: Int, val transform: Attribute.Transform) : KVGTag("text")

    /** Attributes of SVG tags */
    sealed class Attribute(open val name: String) {
        class Id(val value: String) : Attribute("id")

        /** Dimensional attributes: width, height, etc. */
        abstract class Dimension(override val name: String, open val value: Int) : Attribute(name)
        class Width(override val value: Int) : Dimension("width", value)
        class Height(override val value: Int) : Dimension("height", value)

        class ViewBox(val x: Int, val y: Int, val width: Int, val height: Int) : Attribute("viewBox")

        class Transform(val matrix: Matrix) : Attribute("transform") {
            class Matrix(val elements: List<Double>)
        }

        class Style(val attributes: Map<String, String>) : Attribute("style")

        /** KanjiVG-specific attributes, prefixed with "kvg:" */
        abstract class KvgAttribute(override val name: String) : Attribute("kvg:" + name)

        /** KanjiVG-specific attributes which can only appear on strokes (<path> tags) */
        abstract class KvgStrokeAttribute(override val name: String) : KvgAttribute(name)
        class KvgType(val value: String) : KvgStrokeAttribute("type")

        /** KanjiVG-specific attributes which can only appear on groups of strokes (<g> tags) */
        abstract class KvgGroupAttribute(override val name: String) : KvgAttribute(name)
        class KvgElement     (val value: String)   : KvgGroupAttribute("element")
        class KvgOriginal    (val value: String)   : KvgGroupAttribute("original")
        class KvgPosition    (val value: Position) : KvgGroupAttribute("position")
        class KvgVariant     (val value: Boolean)  : KvgGroupAttribute("variant")
        class KvgPartial     (val value: Boolean)  : KvgGroupAttribute("partial")
        class KvgPart        (val value: Int)      : KvgGroupAttribute("part")
        class KvgNumber      (val value: Int)      : KvgGroupAttribute("number")
        class KvgRadical     (val value: Radical)  : KvgGroupAttribute("radical")
        class KvgPhon        (val value: String)   : KvgGroupAttribute("phon")
        class KvgTradForm    (val value: String)   : KvgGroupAttribute("tradForm")
        class KvgRadicalForm (val value: String)   : KvgGroupAttribute("radicalForm")

        enum class Position(val value: String) {
            LEFT("left"), RIGHT("right"),
            TOP("top"), BOTTOM("bottom"),
            NYO("nyo"), TARE("tare"),
            KAMAE("kamae"), KAMAE1("kamae1"), KAMAE2("kamae2")
        }

        enum class Radical(val value: String) {
            GENERAL("general"), NELSON("nelson"), TRADITIONAL("tradit")
        }
    }
}
